package bzh.ya2o.telemetry
package application.config

import cats.implicits._
import ciris.ConfigValue
import io.circe._
import io.circe.generic.semiauto._ // deriveDecoder / deriveEncoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.refined._ // provides decoders for refined types

final case class Config(
  server: ServerConfig,
  messaging: MessagingConfig,
  report: ReportConfig
)

object Config {
  def config[F[_]]: ConfigValue[F, Config] = (
    ServerConfig.config,
    MessagingConfig.config,
    ReportConfig.config
  ).parMapN(Config.apply)

  //implicit val Dec: Decoder[Config] = deriveDecoder[Config]
}
