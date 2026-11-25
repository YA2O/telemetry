package bzh.ya2o.telemetry
package logic

import bzh.ya2o.telemetry.application.config.ReportConfig
import bzh.ya2o.telemetry.integration.messaging.middleware.Delivery
import bzh.ya2o.telemetry.model.CpuMeasurement
import bzh.ya2o.telemetry.TestData.NoOpLogger
import cats.effect.testkit.TestControl
import cats.effect.IO
import cats.effect.Ref
import fs2.Stream
import munit.CatsEffectSuite
import scala.concurrent.duration._

class ReportServiceSuite extends CatsEffectSuite {

  test("report_3Deliveries_acksEachOnceAndCountsThemPerDivision") {
    // Arrange
    val cpus = List(5f, 15f, 19.5f)
    val service = new ReportServiceImpl[IO](ReportConfig(1.second), NoOpLogger)

    val program = for {
      acks <- Ref[IO].of(0)
      deliveries = cpus.map { cpu =>
        Delivery[IO, CpuMeasurement](TestData.measurement("v1.0.0", cpu), acks.update(_ + 1))
      }
      // Act
      reports <- service.report(Stream.emits(deliveries)).take(1).compile.toList
      ackCount <- acks.get
    } yield (reports, ackCount)

    TestControl.executeEmbed(program).map { case (reports, ackCount) =>
      // Assert
      assertEquals(ackCount, 3)
      val counters = reports.flatMap(_.counters)
      assertEquals(counters.map(_.count), List(3L))
      assertEquals(counters.flatMap(_.countPerDivision.toList), List(0 -> 1L, 10 -> 2L))
    }
  }

  test("report_noDeliveries_emitsEmptyReportAndAcksNothing") {
    // Arrange
    val service = new ReportServiceImpl[IO](ReportConfig(1.second), NoOpLogger)

    // Act
    val result = TestControl.executeEmbed(
      service.report(Stream.empty).take(1).compile.toList
    )

    // Assert
    result.map(reports => assertEquals(reports.flatMap(_.counters), Nil))
  }

}
