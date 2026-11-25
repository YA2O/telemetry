package bzh.ya2o.telemetry
package logic

import bzh.ya2o.telemetry.integration.http.QueueDepthSource
import bzh.ya2o.telemetry.TestData.NoOpLogger
import cats.effect.testkit.TestControl
import cats.effect.IO
import cats.effect.Ref
import fs2.concurrent.SignallingRef
import munit.CatsEffectSuite
import scala.concurrent.duration._

class PipelineStatsServiceSuite extends CatsEffectSuite {

  private def source(answer: IO[QueueDepth]): QueueDepthSource[IO] = new QueueDepthSource[IO] {
    override def queueDepth: IO[QueueDepth] = answer
  }

  /** Runs 2 ticks with the given queue-depth source; tick stats are pre-filled before the 1st tick. */
  private def run2Ticks(queueDepth: IO[QueueDepth]): IO[(List[PipelineStats], Option[PipelineStats])] =
    TestControl.executeEmbed(
      for {
        tickStats <- Ref[IO].of(TickStats(3, 600.millis, 300.millis))
        knob <- Ref[IO].of(ProcessingDelay.fromMillis(20).toOption.get)
        latest <- SignallingRef[IO].of(Option.empty[PipelineStats])
        service = new PipelineStatsService[IO](tickStats, knob, source(queueDepth), latest, NoOpLogger)
        emitted <- service.run.take(2).compile.toList
        published <- latest.get
      } yield (emitted, published)
    )

  test("run_queueDepthAvailable_publishesEachTickAndResetsTheTickStats") {
    // Arrange
    val depth = QueueDepth(ready = 12, unacked = 50)

    // Act
    val result = run2Ticks(IO.pure(depth))

    // Assert
    result.map { case (emitted, published) =>
      assertEquals(
        emitted,
        List(
          PipelineStats(3, 200, 300, Some(depth), 20),
          PipelineStats(0, 0, 0, Some(depth), 20) // reset after the 1st tick
        )
      )
      assertEquals(published, emitted.lastOption)
    }
  }

  test("run_queueDepthFails_publishesNoneForTheQueueAndKeepsTicking") {
    // Arrange
    val failing = IO.raiseError[QueueDepth](new RuntimeException("management API down"))

    // Act
    val result = run2Ticks(failing)

    // Assert
    result.map { case (emitted, _) =>
      assertEquals(emitted.map(_.queue), List(None, None))
      assertEquals(emitted.map(_.processedPerSec), List(3L, 0L))
    }
  }

  test("run_queueDepthTooSlow_publishesNoneForTheQueue") {
    // Arrange
    val tooSlow = IO.sleep(5.seconds).as(QueueDepth(1, 1))

    // Act
    val result = run2Ticks(tooSlow)

    // Assert
    result.map { case (emitted, _) => assertEquals(emitted.map(_.queue), List(None, None)) }
  }

}
