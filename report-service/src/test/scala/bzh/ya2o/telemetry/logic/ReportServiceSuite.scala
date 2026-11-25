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
import java.time.Instant
import munit.CatsEffectSuite
import scala.concurrent.duration._

class ReportServiceSuite extends CatsEffectSuite {

  /** A report service with a 1 s report period and the given knob, plus its tick stats. */
  private def serviceWith(delayMillis: Long): IO[(ReportServiceImpl[IO], Ref[IO, TickStats])] =
    for {
      delay <- IO.fromEither(ProcessingDelay.fromMillis(delayMillis).leftMap(new IllegalArgumentException(_)))
      delayRef <- Ref[IO].of(delay)
      tickStats <- Ref[IO].of(TickStats.Empty)
    } yield (new ReportServiceImpl[IO](ReportConfig(1.second), delayRef, tickStats, NoOpLogger), tickStats)

  private def deliveriesOf(cpus: List[Float], ack: IO[Unit]): List[Delivery[IO, CpuMeasurement]] =
    cpus.map(cpu => Delivery[IO, CpuMeasurement](TestData.measurement("v1.0.0", cpu), ack))

  test("report_3Deliveries_acksEachOnceAndCountsThemPerDivision") {
    // Arrange
    val program = for {
      (service, _) <- serviceWith(delayMillis = 0)
      acks <- Ref[IO].of(0)
      deliveries = deliveriesOf(List(5f, 15f, 19.5f), acks.update(_ + 1))
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
    val program = serviceWith(delayMillis = 0).flatMap { case (service, _) =>
      // Act
      service.report(Stream.empty).take(1).compile.toList
    }

    // Assert
    TestControl.executeEmbed(program).map(reports => assertEquals(reports.flatMap(_.counters), Nil))
  }

  test("report_delay400msAnd3Deliveries_countsOnly2InThe1stSecond") {
    // Arrange
    val program = for {
      (service, _) <- serviceWith(delayMillis = 400)
      // Act
      reports <- service.report(Stream.emits(deliveriesOf(List(1f, 2f, 3f), IO.unit))).take(2).compile.toList
    } yield reports

    // Assert
    TestControl.executeEmbed(program).map { reports =>
      assertEquals(reports.map(_.counters.map(_.count).sum), List(2L, 1L), "counted at 400 ms and 800 ms, then 1200 ms")
    }
  }

  test("report_delay400msAndMessagesSentAt0_recordsCountAndDelaysInTickStats") {
    // Arrange
    val sentAt0 = List(1f, 2f, 3f).map { cpu =>
      Delivery[IO, CpuMeasurement](TestData.measurementAt(Instant.EPOCH, "v1.0.0", cpu), IO.unit)
    }
    val program = for {
      (service, tickStats) <- serviceWith(delayMillis = 400)
      // Act
      _ <- service.report(Stream.emits(sentAt0)).take(1).compile.drain
      recorded <- tickStats.get
    } yield recorded

    // Assert: counted at 400 ms and 800 ms before the 1st report at 1 s
    TestControl.executeEmbed(program).map { recorded =>
      assertEquals(recorded, TickStats(2, 1200.millis, 800.millis))
    }
  }

}
