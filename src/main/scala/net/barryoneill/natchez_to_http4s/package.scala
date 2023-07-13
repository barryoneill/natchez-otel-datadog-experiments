package net.barryoneill

import cats.Parallel
import cats.effect.kernel.Temporal
import cats.effect.{Async, Sync}
import cats.implicits._
import com.comcast.ip4s.Port
import org.http4s.client.Client
import org.slf4j.{Logger, LoggerFactory}
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger.getLoggerFromName

import java.lang.management.ManagementFactory
import java.time.format.DateTimeFormatter.ISO_LOCAL_TIME
import java.time.{Instant, ZoneId}
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._
import scala.util.Random

package object natchez_to_http4s {

  def testLogger[F[_]: Sync]: SelfAwareStructuredLogger[F] = getLoggerFromName[F]("testsuite")

  def serviceLogger[F[_]: Sync]: SelfAwareStructuredLogger[F] = getLoggerFromName[F]("appserver")

  val datadogSlfLogger: Logger = LoggerFactory.getLogger("ddogtrace")

  def sleep[F[_]: Async]: F[Unit] = Temporal[F].sleep(Random.between(100, 500).millis) // simulate a small 'busy' pause

  def fmtInstant(time: Instant): String = ISO_LOCAL_TIME.withZone(ZoneId.systemDefault()).format(time)

  def waitForServices[F[_]: Async: Temporal: Parallel](client: Client[F], ports: Port*): F[Unit] =
    ports.parTraverse(p => waitForServiceReady(client, p)).as(())

  def ensureEnvIsSet[F[_]](implicit f: Sync[F]): F[Unit] = {

    // this saves me from wasting my time when I come back to this after a while, as running without these things set throws no error but the DD instrumentation will not trigger
    def fail(msg: String): F[Unit] = f.raiseError(
      new IllegalArgumentException(
        s"""Error: ${msg}
         |The following things are required to get DD-OTel instrumentation to run
         |
         |- The env vars DD_ENV and DD_SERVICE (set these to any value, e.g. 'fooEnv', and 'fooSVC')
         |- The system property `-Ddd.trace.otel.enabled=true` must be set with the JVM startup
         |- The JVM must be started with `-javaagent:/path/to/dd-java-agent.jar` (get it from https://dtdg.co/latest-java-tracer)
         |
         |""".stripMargin
      )
    )

    val mx      = ManagementFactory.getRuntimeMXBean
    val otelOn  = mx.getSystemProperties.asScala.get("dd.trace.otel.enabled").contains("true")
    val agentOn = mx.getInputArguments.asScala.exists(s => s.startsWith("-javaagent") && s.contains("dd"))
    
    for {
      _ <- f.unlessA(sys.env.contains("DD_ENV"))(fail("env DD_ENV missing"))
      _ <- f.unlessA(sys.env.contains("DD_SERVICE"))(fail("env DD_SERVICE missing"))
      _ <- f.unlessA(otelOn)(fail("-Ddd.trace.otel.enabled=true not specified"))
      _ <- f.unlessA(agentOn)(fail("datadog agent not instrumented"))
    } yield ()
  }

  def waitForServiceReady[F[_]: Async: Temporal](client: Client[F], port: Port, retriesLeft: Int = 50): F[Unit] = {
    val period = 100.millis

    for {
      _   <- Async[F].raiseWhen(retriesLeft <= 0)(new Throwable(s"Server at port $port unavailable, no retries left."))
      _   <- Temporal[F].sleep(period)
      att <- client.expect[String](HttpServices.mkHealthcheckURI(port)).attempt
      res <- att match {
        case Right(_) =>
          testLogger.debug(s"service on port $port - ready")
        case Left(e) =>
          testLogger.warn(s"service at $port not yet available (${e.getMessage}) - retrying in $period, $retriesLeft retries left") >>
            waitForServiceReady(client, port, retriesLeft - 1)
      }
    } yield res
  }
}
