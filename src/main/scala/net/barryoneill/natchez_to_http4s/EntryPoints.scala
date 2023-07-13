package net.barryoneill.natchez_to_http4s

import cats.effect.Sync
import cats.implicits.toFunctorOps
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.{IdGenerator, SdkTracerProvider}
import natchez.EntryPoint
import natchez.opentelemetry.OpenTelemetry

import java.util.concurrent.atomic.AtomicLong

object EntryPoints {

  /* some OpenTelemetry functions deliver entrypoints in F[], but some don't
   * hence a few Sync[F].delay wrappers so I can easily swap experiments around in Main */


  def entryPointUnderTest[F[_]: Sync]: F[EntryPoint[F]] = experiment_B_GlobalEP

  def experiment_A_SimpleUsage[F[_]: Sync]: F[EntryPoint[F]] =
    OpenTelemetry.entryPointFor(MyOtelSDK).widen

  def experiment_B_GlobalEP[F[_]: Sync]: F[EntryPoint[F]] =
    OpenTelemetry.globalEntryPoint[F]().widen

  def experiment_C_SDKTracerRef[F[_] : Sync]: F[EntryPoint[F]] = Sync[F].delay {
    OpenTelemetry.entryPointFor(
      otel = EntryPoints.MyOtelSDK,
      tracer = EntryPoints.MyOtelSDK.getTracer("natchez"),
      prefix = None
    )
  }

  def experiment_D_NamedTracer[F[_]: Sync]: F[EntryPoint[F]] = Sync[F].delay {

    // what's interesting here is that although MyOtelSDK is provided MyOtelTP as the tracer provider,
    // it doesn't seem to get used.  But when I explicitly pass it here, it is invoked..
    // though, no datadog traces get generated at all, even with direct entrypoint usage :/
    OpenTelemetry.entryPointFor(
      otel = EntryPoints.MyOtelSDK,
      tracer = MyOtelTP.tracerBuilder("natchez").build(), // perhaps "natchez.opentelemetry"
      prefix = None
    )
  }


  val MyOtelTP: SdkTracerProvider =
    SdkTracerProvider
      .builder()
      .setIdGenerator(new CustomDataDogIDGenerator)
      .build()

  val MyOtelSDK: OpenTelemetrySdk =
    OpenTelemetrySdk
      .builder()
      .setTracerProvider(MyOtelTP)
      .buildAndRegisterGlobal()

  // a crude custom impl
  class CustomDataDogIDGenerator extends IdGenerator {

    val traceIdGen = new AtomicLong(1)
    val spanIdGen  = new AtomicLong(1)

    override def generateSpanId(): String = {
      val spanId = String.format("%016d", spanIdGen.incrementAndGet())
      datadogSlfLogger.info(s"created custom spanID: $spanId")
      spanId
    }

    override def generateTraceId(): String = {
      val traceId = String.format("%032d", traceIdGen.incrementAndGet())
      datadogSlfLogger.info(s"created custom traceId: $traceId")
      traceId
    }
  }

}
