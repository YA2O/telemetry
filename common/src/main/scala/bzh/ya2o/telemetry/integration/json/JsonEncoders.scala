package bzh.ya2o.telemetry
package integration.json

import bzh.ya2o.telemetry.model.CpuMeasurement
import bzh.ya2o.telemetry.model.CpuMeasurement._
import io.circe.Encoder

object JsonEncoders {
  implicit private val DeviceIdEncoder: Encoder[DeviceId] = Encoder[String].contramap(_.value)

  implicit private val ClientVersionEncoder: Encoder[ClientVersion] = Encoder[String].contramap(_.value)

  implicit private val CpuEncoder: Encoder[Cpu] = Encoder[Float].contramap(_.value)

  implicit val MeasurementEncoder: Encoder[CpuMeasurement] =
    Encoder.forProduct4("deviceId", "timestamp", "clientVersion", "cpu") { m: CpuMeasurement =>
      (m.deviceId, m.timestamp, m.clientVersion, m.cpu)
    }

}
