package bzh.ya2o.telemetry
package simulator

import cats.implicits._
import ciris._
import ciris.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.Positive
import org.http4s.Uri

final case class SimulatorConfig(
  target: Uri,
  ratePerSec: SimulatorConfig.PositiveInt,
  concurrency: SimulatorConfig.PositiveInt
)

object SimulatorConfig {
  type PositiveInt = Int Refined Positive

  implicit private val uriDecoder: ConfigDecoder[String, Uri] =
    ConfigDecoder[String].mapOption("Uri")(Uri.fromString(_).toOption)

  def config[F[_]]: ConfigValue[F, SimulatorConfig] =
    (
      prop("simulator.target").as[Uri].default(Uri.unsafeFromString("http://localhost:8080/telemetry/cpu")),
      prop("simulator.ratePerSec").as[PositiveInt].default(100),
      prop("simulator.concurrency").as[PositiveInt].default(16)
    ).mapN(SimulatorConfig.apply)

}
