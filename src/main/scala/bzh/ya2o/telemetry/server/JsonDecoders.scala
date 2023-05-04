package bzh.ya2o.telemetry.server

import bzh.ya2o.telemetry.model.Measurement
import bzh.ya2o.telemetry.model.Measurement._
import io.circe.Decoder

object JsonDecoders {
  implicit private val DeviceIdDecoder: Decoder[DeviceId] = Decoder[String].emap(DeviceId(_).toEither)

  implicit private val ClientVersionDecoder: Decoder[ClientVersion] =
    Decoder[String].emap(ClientVersion(_).toEither)

  implicit private val CpuDecoder: Decoder[Cpu] = Decoder[Float].emap(Cpu(_).toEither)

  implicit val MeasurmentDecoder: Decoder[Measurement] =
    Decoder.forProduct4("deviceId", "timestamp", "clientVersion", "cpu")(Measurement.apply)
}
