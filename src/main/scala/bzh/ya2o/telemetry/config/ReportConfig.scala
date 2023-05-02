package bzh.ya2o.telemetry
package config

import cats.implicits._
import ciris._
import ciris.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.SECONDS

final case class ReportConfig(
  period: FiniteDuration
)

object ReportConfig {
  type PositiveLong = Long Refined Positive
  type PositiveInt = Int Refined Positive

  def config[F[_]]: ConfigValue[F, ReportConfig] =
    prop("report.periodSec").as[PositiveLong].default(10L).map { period =>
      ReportConfig(FiniteDuration(period, SECONDS))
    }
}
