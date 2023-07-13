package net.barryoneill.natchez_to_http4s

import cats.effect.{Async, Resource}
import cats.implicits._
import com.comcast.ip4s.Port
import io.chrisdavenport.fiberlocal.GenFiberLocal
import io.chrisdavenport.natchezhttp4sotel.{ServerMiddleware => DavenportMiddleware}
import natchez.http4s.NatchezMiddleware
import natchez.http4s.implicits.toEntryPointOps
import natchez.{EntryPoint, Trace, TraceValue}
import org.http4s.Uri.Path
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.server.EmberServerBuilder

import scala.concurrent.duration.DurationInt

object HttpServices {

  def withNatchez[F[_]: Async](ep: EntryPoint[F], port: Port): Resource[F, Unit] =
    mkServer(
      port,
      ep.liftT(NatchezMiddleware.server(testRoutes)).orNotFound
    )

  def withDavenport[F[_]: Async: GenFiberLocal](ep: EntryPoint[F], port: Port): Resource[F, Unit] =
    mkServer(
      port,
      DavenportMiddleware
        .default(ep)
        .buildHttpApp { implicit T: natchez.Trace[F] => testRoutes[F].orNotFound }
    )

  val HealthCheckPath: Path = Path.Root / "healthCheck"
  val BusinessPath: Path    = Path.Root / "biz"

  val BusinessResult = "business done"

  def mkHealthcheckURI(port: Port): Uri = Uri.unsafeFromString(s"http://localhost:$port$HealthCheckPath")
  def mkBusinessURI(port: Port): Uri    = Uri.unsafeFromString(s"http://localhost:$port$BusinessPath")

  def testRoutes[F[_]: Async: Trace]: HttpRoutes[F] = {

    object dsl extends Http4sDsl[F]
    import dsl._

    HttpRoutes.of[F] {
      case GET -> HealthCheckPath => Ok("ready")
      case POST -> BusinessPath   => sleep >> fakeBizLogic >> sleep >> Ok(BusinessResult)
    }
  }

  private def fakeBizLogic[F[_]: Async: Trace]: F[Unit] =
    Trace[F].span("fakeBizLogic") {
      for {
        _ <- Trace[F].put("businessAttr" -> TraceValue.StringValue("STONKS"))
        _ <- sleep
        _ <- serviceLogger[F].info("http service executing 'business' logic")
      } yield ()
    }

  def mkServer[F[_]: Async](port: Port, app: HttpApp[F]): Resource[F, Unit] = {

    object dsl extends Http4sDsl[F]
    import dsl._

    EmberServerBuilder
      .default[F]
      .withPort(port)
      .withHttpApp(app)
      .withIdleTimeout(2.seconds)
      .withErrorHandler { case t: Throwable =>
        serviceLogger[F].error(s"server on port $port b0rked, ${t.getClass.getSimpleName}:${t.getMessage}") >> InternalServerError()
      }
      .build
      .evalMap(_ => serviceLogger.debug(s"service on port $port started"))
      .onFinalize {
        serviceLogger.debug(s"service on port $port shutdown")
      }
  }

}
