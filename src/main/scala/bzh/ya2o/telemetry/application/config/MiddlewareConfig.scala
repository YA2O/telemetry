package bzh.ya2o.telemetry
package application.config

import ciris._
import ciris.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric._
import MiddlewareConfig.PositiveInt

final case class MiddlewareConfig(
  maxQueued: PositiveInt
)

object MiddlewareConfig {
  type PositiveInt = Int Refined Positive

  def config[F[_]]: ConfigValue[F, MiddlewareConfig] =
    prop("middleware.max_queued").as[PositiveInt].default(100).map(MiddlewareConfig.apply)
}
