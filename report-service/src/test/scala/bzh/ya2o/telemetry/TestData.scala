package bzh.ya2o.telemetry

import bzh.ya2o.telemetry.application.logging.Logger
import bzh.ya2o.telemetry.model.CpuMeasurement
import bzh.ya2o.telemetry.model.CpuMeasurement.ClientVersion
import bzh.ya2o.telemetry.model.CpuMeasurement.Cpu
import bzh.ya2o.telemetry.model.CpuMeasurement.DeviceId
import cats.effect.IO
import java.time.Instant

object TestData {

  def measurement(clientVersion: String, cpu: Float): CpuMeasurement =
    measurementAt(Instant.parse("2020-01-01T00:00:00Z"), clientVersion, cpu)

  def measurementAt(timestamp: Instant, clientVersion: String, cpu: Float): CpuMeasurement =
    CpuMeasurement(
      valid(DeviceId("device-1")),
      timestamp,
      valid(ClientVersion(clientVersion)),
      valid(Cpu(cpu))
    )

  private def valid[A](validated: Validated_[A]): A =
    validated.fold(error => throw new IllegalArgumentException(error), identity)

  object NoOpLogger extends Logger[IO] {
    override def error(msg: String): IO[Unit] = IO.unit
    override def warn(msg: String): IO[Unit] = IO.unit
    override def info(msg: String): IO[Unit] = IO.unit
    override def debug(msg: String): IO[Unit] = IO.unit
  }

}
