package bzh.ya2o.telemetry.server

import bzh.ya2o.telemetry.model.Measurement._
import bzh.ya2o.telemetry.model.Measurement

import io.circe.Decoder

object JsonDecoders {
  implicit private val deviceIdDecoder: Decoder[DeviceId] = Decoder[String].emap(DeviceId.apply)

  implicit private val clientVersionDecoder: Decoder[ClientVersion] =
    Decoder[String].emap(ClientVersion.apply)

  implicit private val cpuDecoder: Decoder[Cpu] = Decoder[Float].emap(Cpu.apply)

  implicit val dataDecoder: Decoder[Measurement] =
    Decoder.forProduct4("deviceId", "timestamp", "clientVersion", "cpu")(Measurement.apply)
}
