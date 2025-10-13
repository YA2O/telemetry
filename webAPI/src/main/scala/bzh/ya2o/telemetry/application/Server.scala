package bzh.ya2o.telemetry
package application

import bzh.ya2o.telemetry.application.config.ServerConfig
import bzh.ya2o.telemetry.application.logging.Logger
import bzh.ya2o.telemetry.integration.http.Routes
import cats.effect.Async
import cats.effect.Resource
import com.comcast.ip4s._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.middleware.Logger
import org.http4s.server.Server
import org.http4s.HttpApp

object Server {

  def resource[F[_]](
    routes: Routes[F],
    config: ServerConfig,
    logger: Logger[F]
  )(
    implicit F: Async[F]
  ): Resource[F, Server] = {
    val httpApp: HttpApp[F] = Logger.httpApp(
      logHeaders = true,
      logBody = true
    )(routes.publicRoutes.orNotFound)

    val port: Port = Port
      .fromInt(config.port.value)
      .getOrElse(throw new IllegalStateException(s"Invalid port: [${config.port}]"))

    EmberServerBuilder
      .default[F](F)
      //.withHttpApp(host)
      .withPort(port)
      .withHttpApp(httpApp)
      .build
      .evalTap(server => logger.info(s"HTTP server started on [${server.address}]"))
      .onFinalize(logger.info("Server stopped"))
  }

}
