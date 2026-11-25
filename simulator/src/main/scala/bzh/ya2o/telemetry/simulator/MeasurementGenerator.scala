package bzh.ya2o.telemetry
package simulator

import bzh.ya2o.telemetry.model.CpuMeasurement
import bzh.ya2o.telemetry.model.CpuMeasurement.ClientVersion
import bzh.ya2o.telemetry.model.CpuMeasurement.Cpu
import bzh.ya2o.telemetry.model.CpuMeasurement.DeviceId
import cats.data.NonEmptyVector
import cats.effect.std.Random
import cats.effect.Temporal
import cats.implicits._
import java.time.Instant

/** Produces measurements stamped with the current time, from a fixed pool of devices and
  * client versions, with a CPU value in [0, 100] in steps of 0.1.
  */
class MeasurementGenerator[F[_]](
  random: Random[F],
  devices: NonEmptyVector[DeviceId],
  clientVersions: NonEmptyVector[ClientVersion]
)(
  implicit F: Temporal[F]
) {

  def next: F[CpuMeasurement] =
    for {
      now <- F.realTime
      device <- random.elementOf(devices.toVector)
      clientVersion <- random.elementOf(clientVersions.toVector)
      tenths <- random.betweenInt(0, 1001)
      cpu <- F.fromEither(Cpu(tenths / 10f).toEither.leftMap(new IllegalStateException(_)))
    } yield CpuMeasurement(device, Instant.ofEpochMilli(now.toMillis), clientVersion, cpu)

}

object MeasurementGenerator {

  /** 100 devices and 3 client versions. */
  def make[F[_]](random: Random[F])(implicit F: Temporal[F]): F[MeasurementGenerator[F]] =
    for {
      devices <- (1 to 100).toVector.traverse(i => validOrRaise(DeviceId(s"device-$i")))
      versions <- Vector("v1.0.0", "v1.1.0", "v2.0.0").traverse(v => validOrRaise(ClientVersion(v)))
    } yield new MeasurementGenerator[F](
      random,
      NonEmptyVector.fromVectorUnsafe(devices),
      NonEmptyVector.fromVectorUnsafe(versions)
    )

  private def validOrRaise[F[_], A](validated: Validated_[A])(implicit F: Temporal[F]): F[A] =
    F.fromEither(validated.toEither.leftMap(new IllegalArgumentException(_)))

}
