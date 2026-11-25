package bzh.ya2o.telemetry
package logic

import bzh.ya2o.telemetry.application.config.ReportConfig
import bzh.ya2o.telemetry.integration.messaging.middleware.Delivery
import bzh.ya2o.telemetry.model.CpuMeasurement
import bzh.ya2o.telemetry.TestData.NoOpLogger
import cats.effect.testkit.TestControl
import cats.effect.IO
import cats.effect.Ref
import cats.implicits._
import fs2.Stream
import munit.CatsEffectSuite
import scala.concurrent.duration._

class ReportServiceSuite extends CatsEffectSuite {

  test("report_3Deliveries_acksEachOnceAndCountsThemPerDivision") {
    // Arrange
    val cpus = List(5f, 15f, 19.5f)

    val program = for {
      delay <- Ref[IO].of(ProcessingDelay.None)
      service = new ReportServiceImpl[IO](ReportConfig(1.second), delay, NoOpLogger)
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
    val program = Ref[IO].of(ProcessingDelay.None).flatMap { delay =>
      val service = new ReportServiceImpl[IO](ReportConfig(1.second), delay, NoOpLogger)
      // Act
      service.report(Stream.empty).take(1).compile.toList
    }
    val result = TestControl.executeEmbed(program)

    // Assert
    result.map(reports => assertEquals(reports.flatMap(_.counters), Nil))
  }

  test("report_delay400msAnd3Deliveries_countsOnly2InThe1stSecond") {
    // Arrange
    val deliveries = List(1f, 2f, 3f).map(cpu => Delivery[IO, CpuMeasurement](TestData.measurement("v1.0.0", cpu), IO.unit))
    val program = for {
      delay <- IO.fromEither(ProcessingDelay.fromMillis(400).leftMap(new IllegalArgumentException(_)))
      delayRef <- Ref[IO].of(delay)
      service = new ReportServiceImpl[IO](ReportConfig(1.second), delayRef, NoOpLogger)
      // Act
      reports <- service.report(Stream.emits(deliveries)).take(2).compile.toList
    } yield reports

    // Assert
    TestControl.executeEmbed(program).map { reports =>
      assertEquals(reports.map(_.counters.map(_.count).sum), List(2L, 1L), "counted at 400 ms and 800 ms, then 1200 ms")
    }
  }

}
