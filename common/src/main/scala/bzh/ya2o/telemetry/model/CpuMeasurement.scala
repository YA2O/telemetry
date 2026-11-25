package bzh.ya2o.telemetry
package model

import bzh.ya2o.telemetry.model.CpuMeasurement._
import cats.implicits.catsSyntaxValidatedId
import java.time.Instant

final case class CpuMeasurement(
  deviceId: DeviceId,
  timestamp: Instant,
  clientVersion: ClientVersion,
  cpu: Cpu
)

object CpuMeasurement {
  sealed abstract case class DeviceId private (value: String)
  object DeviceId {
    def apply(value: String): Validated_[DeviceId] = {
      if (isValid(value))
        unsafeApply(value).valid
      else s"Invalid deviceId: [$value]!".invalid
    }
    private def isValid(value: String): Boolean = value.nonEmpty
    private def unsafeApply(value: String): DeviceId = new DeviceId(value) {}
  }

  sealed abstract case class Cpu private (value: Float)
  object Cpu {
    def apply(value: Float): Validated_[Cpu] = {
      if (isValid(value))
        unsafeApply(value).valid
      else s"invalid cpu: [$value]".invalid
    }
    private def isValid(value: Float): Boolean = value >= 0 && value <= 100
    private def unsafeApply(value: Float): Cpu = new Cpu(value) {}
  }

  sealed abstract case class ClientVersion private (value: String)
  object ClientVersion {
    private val regex = """^v([0-9-]+\.)+([0-9]*)$""".r
    def apply(value: String): Validated_[ClientVersion] = {
      if (isValid(value))
        unsafeApply(value).valid
      else s"invalid clientVersion: [$value]".invalid
    }
    private def isValid(value: String): Boolean = regex.matches(value)
    private def unsafeApply(value: String): ClientVersion = new ClientVersion(value) {}
  }

}
