package bzh.ya2o.telemetry
package integration.http

import bzh.ya2o.telemetry.model.CpuMeasurement
import bzh.ya2o.telemetry.model.CpuMeasurement._
import io.circe.Decoder

object JsonDecoders {
  implicit private val DeviceIdDecoder: Decoder[DeviceId] = Decoder[String].emap(DeviceId(_).toEither)

  implicit private val ClientVersionDecoder: Decoder[ClientVersion] =
    Decoder[String].emap(ClientVersion(_).toEither)

  implicit private val CpuDecoder: Decoder[Cpu] = Decoder[Float].emap(Cpu(_).toEither)

  implicit val MeasurmentDecoder: Decoder[CpuMeasurement] =
    Decoder.forProduct4("deviceId", "timestamp", "clientVersion", "cpu")(CpuMeasurement.apply)
}
