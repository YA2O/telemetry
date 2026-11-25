package bzh.ya2o.telemetry
package simulator

import cats.effect.std.Random
import cats.effect.IO
import cats.implicits._
import munit.CatsEffectSuite

class MeasurementGeneratorSuite extends CatsEffectSuite {

  test("next_1000Draws_staysWithinThePoolsAndTheCpuRange") {
    // Arrange
    val generated = for {
      random <- Random.scalaUtilRandomSeedInt[IO](7)
      generator <- MeasurementGenerator.make[IO](random)
      // Act
      measurements <- generator.next.replicateA(1000)
    } yield measurements

    // Assert
    generated.map { measurements =>
      val cpus = measurements.map(_.cpu.value)
      assert(cpus.forall(cpu => cpu >= 0f && cpu <= 100f), s"cpu out of range: [${cpus.filter(c => c < 0f || c > 100f)}]")
      assertEquals(measurements.map(_.clientVersion.value).toSet, Set("v1.0.0", "v1.1.0", "v2.0.0"))
      assert(measurements.map(_.deviceId.value).toSet.subsetOf((1 to 100).map(i => s"device-$i").toSet))
    }
  }

}
