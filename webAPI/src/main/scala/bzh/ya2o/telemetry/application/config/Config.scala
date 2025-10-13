package bzh.ya2o.telemetry
package application.config

import cats.implicits._
import ciris.ConfigValue

final case class Config(
  server: ServerConfig,
  messaging: MessagingConfig
)

object Config {
  def config[F[_]]: ConfigValue[F, Config] = (
    ServerConfig.config,
    MessagingConfig.config
  ).parMapN(Config.apply)
}
