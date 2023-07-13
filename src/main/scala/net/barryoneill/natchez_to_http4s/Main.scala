package net.barryoneill.natchez_to_http4s

import cats.effect._
import cats.implicits._
import com.comcast.ip4s.{IpLiteralSyntax, Port}
import natchez.EntryPoint
import org.http4s.Method.POST
import org.http4s.client.Client
import org.http4s.client.dsl.io._
import org.http4s.ember.client.EmberClientBuilder

// DD_ENV=dev;DD_SERVICE=foo-bar
object Main extends IOApp.Simple {

  val PortA: Port = port"8000"
  val PortB: Port = port"9000"

  override def run: IO[Unit] = EmberClientBuilder.default[IO].build.use { client =>
    EntryPoints.entryPointUnderTest[IO].flatMap { epUnderTest =>
      val servers: IO[Unit] = List(
        HttpServices.withNatchez[IO](epUnderTest, PortA),
        HttpServices.withDavenport[IO](epUnderTest, PortB)
      ).parSequence.useForever

      val testSuite: IO[Unit] =
        for {
          _         <- waitForServices(client, PortA, PortB)
          ddCapture <- DatadogCapturer.start[IO]

          _ <- invokeNatchezEndpointDirectly(epUnderTest)
          _ <- postBusinessEndpoint(client, PortA)
          _ <- postBusinessEndpoint(client, PortB)

          _ <- ddCapture.printTraces

        } yield ()

      ensureEnvIsSet[IO] >> servers.race(testSuite).as(())
    }
  }

  def invokeNatchezEndpointDirectly(ep: EntryPoint[IO]): IO[Unit] =
    ep.root("Direct_Endpoint_Usage").use { rootSpan =>
      (rootSpan.traceId, rootSpan.kernel).tupled.flatMap { case (id, kernel) =>
        serviceLogger[IO].info(s"direct use of entrypoint - Span Id:${id.getOrElse("n/a")}, kernel:$kernel")
      }
    }

  def postBusinessEndpoint(client: Client[IO], port: Port): IO[Unit] =
    for {
      body <- client.expect[String](POST(HttpServices.mkBusinessURI(port)))
      r    <- IO.raiseUnless(body == HttpServices.BusinessResult)(new Throwable(s"Unexpected body: $body"))
    } yield r

}
