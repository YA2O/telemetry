package bzh.ya2o.telemetry
package logic

import io.circe.syntax._
import java.time.Instant
import munit.FunSuite
import scala.concurrent.duration._

class PipelineStatsSuite extends FunSuite {

  test("tickStatsAdd_delays_sumsAndKeepsTheMaximum") {
    // Arrange
    val delays = List(300.millis, 100.millis, 500.millis)

    // Act
    val result = delays.foldLeft(TickStats.Empty)(_.add(_))

    // Assert
    assertEquals(result, TickStats(3, 900.millis, 500.millis))
  }

  test("delayBetween_cases_isTheTimeElapsedAndNeverNegative") {
    // Arrange
    val sentAt = Instant.ofEpochMilli(10000)
    val cases = List(
      10000.millis -> Duration.Zero, // counted the instant it was sent
      10250.millis -> 250.millis,
      9000.millis -> Duration.Zero // the sender's clock is ahead
    )

    cases.foreach { case (countedAt, expected) =>
      // Act
      val result = TickStats.delayBetween(sentAt, countedAt)

      // Assert
      assertEquals(result, expected, s"countedAt = [$countedAt]")
    }
  }

  test("of_tickAndQueueAndKnob_computesAverageAndCarriesTheRest") {
    // Arrange
    val cases = List(
      (TickStats(4, 1000.millis, 400.millis), PipelineStats(4, 250, 400, Some(QueueDepth(7, 50)), 50)),
      (TickStats.Empty, PipelineStats(0, 0, 0, Some(QueueDepth(7, 50)), 50))
    )
    val knob = ProcessingDelay.fromMillis(50).toOption.get

    cases.foreach { case (tick, expected) =>
      // Act
      val result = PipelineStats.of(tick, Some(QueueDepth(7, 50)), knob)

      // Assert
      assertEquals(result, expected, s"tick = [$tick]")
    }
  }

  test("encoder_queueMissing_writesNull") {
    // Arrange
    val stats = PipelineStats(1, 2, 3, None, 0)

    // Act
    val json = stats.asJson.noSpaces

    // Assert
    assertEquals(json, """{"processedPerSec":1,"delayAvgMillis":2,"delayMaxMillis":3,"queue":null,"knobDelayMillis":0}""")
  }

}
