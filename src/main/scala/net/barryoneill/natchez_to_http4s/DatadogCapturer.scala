package net.barryoneill.natchez_to_http4s

import cats.effect.Sync
import cats.effect.kernel.Async
import cats.implicits._
import com.github.freva.asciitable.HorizontalAlign.LEFT
import com.github.freva.asciitable.{AsciiTable, Column, ColumnData, HorizontalAlign}
import datadog.trace.api.interceptor.{MutableSpan, TraceInterceptor}
import datadog.trace.api.{DD128bTraceId, DD64bTraceId, DDTraceId, GlobalTracer}

import java.time.Instant
import java.util
import java.util.concurrent.TimeUnit.NANOSECONDS
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

// a bunch of routines to capture and render the datadog generated traces
object DatadogCapturer {

  // use Datadog's 'TraceInterceptor' API to capture the traces that the agent would be forwarding
  def start[F[_]: Async]: F[DatadogCapturer[F]] = {

    // warning: here be mutation, but this is just for testing
    val captured: ListBuffer[DDSpanView] = new ListBuffer()

    Async[F].async_[DatadogCapturer[F]] { callback =>
      GlobalTracer
        .get()
        .addTraceInterceptor(
          new TraceInterceptor {
            override def onTraceComplete(spans: util.Collection[_ <: MutableSpan]): util.Collection[_ <: MutableSpan] = {
              captured.addAll(
                spans.asScala.map(DDSpanView.fromMutableSpan)
              )
              spans
            }

            override def priority(): Int = 1000
          }
        )

      callback(Right(DatadogCapturer[F](captured)))
    }

  }

}

case class DatadogCapturer[F[_]: Sync](captured: ListBuffer[DDSpanView]) {

  def formatTraces: String = {

    def column(name: String, f: DDSpanView => String = _.toString, align: HorizontalAlign = LEFT): ColumnData[DDSpanView] =
      new Column().header(name).dataAlign(align).`with`[DDSpanView](span => f(span))

    AsciiTable
      .builder()
      .border(AsciiTable.BASIC_ASCII_NO_DATA_SEPARATORS)
      .data(
        captured.asJavaCollection,
        List(
          column("When", s => fmtInstant(s.startTime)),
          column("Operation Name", _.operationName),
          column("Resource Name", _.resourceName),
          column(
            "DD Trace Id",
            _.ddTraceId match {
              case t: DD128bTraceId => s"128b: ${t.toHexString}"
              case t: DD64bTraceId  => s" 64b: ${t.toHexString}"
            }
          ),
          column("DD Span Id", _.ddSpanId.toString),
          column("DDParentSpanId", _.ddSpanParentId.toString),
          column("Tags", _.tags.map { case (k, v) => s"$k=$v" }.toList.sorted.mkString("\n")),
          column("Service Name", _.serviceName),
          column("Span Type", _.spanType),
          column("Duration (ms)", _.durationMS.toString)
        ).asJava
      )
      .asString()
  }

  def printTraces: F[Unit] =
    for {
      _ <- serviceLogger[F].info(s"Would be sending ${captured.length} span(s) to datadog:\n$formatTraces")
      invalidIds = captured.count(_.hasEmptyId)
      _ <- Sync[F].whenA(invalidIds > 0) {
        serviceLogger[F].error(s"$invalidIds out of ${captured.size} datadog spans had empty traceIds")
      }
    } yield ()

}

case class DDSpanView(
    ddTraceId: DDTraceId,
    ddSpanId: Long,
    ddSpanParentId: Long,
    tags: Map[String, Any],
    spanType: String,
    operationName: String,
    resourceName: String,
    serviceName: String,
    startTime: Instant,
    durationMS: Long
) {
  val hasEmptyId: Boolean = ddTraceId.toLong <= 0
}

object DDSpanView {

  // datadog.trace.agent.core.DDSpan (i.e. _ <: MutableSpan) is internal to the agent instrumentation, hence this hackery :(
  def fromMutableSpan[T <: MutableSpan](datadogSpan: T): DDSpanView =
    DDSpanView(
      ddTraceId = getInternalDDField[DDTraceId](datadogSpan, "traceId"),
      ddSpanId = getInternalDDField[Long](datadogSpan, "spanId"),
      ddSpanParentId = getInternalDDField[Long](datadogSpan, "parentId"),
      tags = datadogSpan.getTags.asScala.toMap,
      spanType = datadogSpan.getSpanType,
      operationName = datadogSpan.getOperationName.toString,
      resourceName = datadogSpan.getResourceName.toString,
      serviceName = datadogSpan.getServiceName,
      startTime = Instant.ofEpochMilli(FiniteDuration(datadogSpan.getStartTime, NANOSECONDS).toMillis),
      durationMS = FiniteDuration(datadogSpan.getDurationNano, NANOSECONDS).toMillis
    )

  private[this] def getInternalDDField[A](ddSpan: Any, fieldName: String): A =
    try {
      val contextField = ddSpan.getClass.getDeclaredField("context")
      contextField.setAccessible(true)
      val context = contextField.get(ddSpan)

      val targetField = context.getClass.getDeclaredField(fieldName)
      targetField.setAccessible(true)
      val target = targetField.get(context)

      target.asInstanceOf[A]

    } catch {
      case e: Throwable =>
        throw new IllegalStateException(s"Failed to load field '$fieldName', got ${e.getClass.getSimpleName}:${e.getMessage}")
    }
}
