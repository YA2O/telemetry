package bzh.ya2o.telemetry
package config

import cats.implicits._
import ciris.ConfigValue

final case class Config(
  server: ServerConfig,
  middleware: MiddlewareConfig,
  report: ReportConfig
)

object Config {
  def config[F[_]]: ConfigValue[F, Config] = (
    ServerConfig.config,
    MiddlewareConfig.config,
    ReportConfig.config
  ).parMapN(Config.apply)
}
