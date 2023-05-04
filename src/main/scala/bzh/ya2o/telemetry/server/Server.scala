package bzh.ya2o.telemetry
package server

import bzh.ya2o.telemetry.config.ServerConfig
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
    routes: Routes[F],
    config: ServerConfig
  )(
    implicit F: Async[F]
  ): Resource[F, Unit] = {
    val httpApp: HttpApp[F] = Logger.httpApp(logHeaders = true, logBody = true)(
      routes.publicRoutes.orNotFound
    )

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
          .withHttpApp(httpApp)
          .build >>
          Resource.eval[F, Unit](F.never)
      )
    } yield resource
  }

}
