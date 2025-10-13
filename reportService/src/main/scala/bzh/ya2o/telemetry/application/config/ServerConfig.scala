package bzh.ya2o.telemetry
package application.config

import cats.implicits._
import ciris._
import ciris.refined._
import eu.timepit.refined.auto._
import eu.timepit.refined.types.net.PortNumber

final case class ServerConfig(
  port: PortNumber
)

object ServerConfig {
  def config[F[_]]: ConfigValue[F, ServerConfig] =
    prop("server.port").as[PortNumber].default(8081).map(ServerConfig.apply)
}
