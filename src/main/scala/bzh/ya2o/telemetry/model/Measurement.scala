package bzh.ya2o.telemetry.model

import bzh.ya2o.telemetry.model.Measurement._

import java.time.Instant

final case class Measurement(
  deviceId: DeviceId,
  timestamp: Instant,
  clientVersion: ClientVersion,
  cpu: Cpu
)

object Measurement {
  sealed abstract case class DeviceId(value: String)
  object DeviceId {
    def apply(value: String): Either[String, DeviceId] = {
      if (isValid(value))
        Right(unsafeApply(value))
      else Left(s"Invalid deviceId: [$value]!")
    }
    private def isValid(value: String): Boolean = value.nonEmpty
    private def unsafeApply(value: String): DeviceId = new DeviceId(value) {}
  }

  sealed abstract case class Cpu(value: Float)
  object Cpu {
    def apply(value: Float): Either[String, Cpu] = {
      if (isValid(value))
        Right(unsafeApply(value))
      else Left(s"Invalid cpu: [$value]!")
    }
    private def isValid(value: Float): Boolean = value >= 0 && value <= 100
    private def unsafeApply(value: Float): Cpu = new Cpu(value) {}
  }

  sealed abstract case class ClientVersion(value: String)
  object ClientVersion {
    private val regex = """^v([0-9-]+\.)+([0-9]*)$""".r
    def apply(value: String): Either[String, ClientVersion] = {
      if (isValid(value))
        Right(unsafeApply(value))
      else Left(s"Invalid clientVersion: [$value]!")
    }
    private def isValid(value: String): Boolean = regex.matches(value)
    private def unsafeApply(value: String): ClientVersion = new ClientVersion(value) {}
  }
}
