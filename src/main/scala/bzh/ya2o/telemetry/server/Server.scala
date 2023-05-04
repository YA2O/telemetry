package bzh.ya2o.telemetry
package server

import bzh.ya2o.telemetry.config.ServerConfig
import bzh.ya2o.telemetry.logging.Logger
import cats.effect.Async
import cats.effect.Resource
import cats.syntax.all._
import com.comcast.ip4s._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.middleware.Logger
import org.http4s.HttpApp

object Server {

  def resource[F[_]](
    publisher: Publisher[F],
    config: ServerConfig,
    logger: Logger[F]
  )(
    implicit F: Async[F]
  ): Resource[F, Unit] = {

    val finalHttpApp: HttpApp[F] = {
      val httpApp = Routes.publicRoutes(publisher, logger).orNotFound
      Logger.httpApp(logHeaders = true, logBody = true)(httpApp)
    }

    for {
      port <- Resource.pure(
        Port
          .fromInt(config.port.value)
          .getOrElse(throw new IllegalStateException(s"Invalid port: [${config.port}]."))
      )
      resource <- (
        EmberServerBuilder
          .default[F]
          .withPort(port)
          .withHttpApp(finalHttpApp)
          .build >>
          Resource.eval[F, Unit](F.never)
      )
    } yield resource
  }

}
