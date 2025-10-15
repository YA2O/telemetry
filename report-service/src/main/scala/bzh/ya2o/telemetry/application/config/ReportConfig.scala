package bzh.ya2o.telemetry
package application.config

import cats.implicits._
import ciris._
import ciris.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric._
import io.circe._
import io.circe.generic.semiauto._ // deriveDecoder / deriveEncoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.refined._ // provides decoders for refined types
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.SECONDS

final case class ReportConfig(
  period: FiniteDuration
)

object ReportConfig {
  type PositiveLong = Long Refined Positive

  def config[F[_]]: ConfigValue[F, ReportConfig] =
    prop("report.periodSec").as[PositiveLong].default(10L).map { period =>
      ReportConfig(FiniteDuration(period, SECONDS))
    }

  //implicit val Dec: Decoder[ReportConfig] = deriveDecoder[ReportConfig]
}
