package bzh.ya2o.telemetry
package integration.http

import bzh.ya2o.telemetry.logic.PipelineStats
import bzh.ya2o.telemetry.logic.ProcessingDelay
import bzh.ya2o.telemetry.logic.QueueDepth
import cats.effect.testkit.TestControl
import cats.effect.IO
import cats.effect.Ref
import fs2.concurrent.SignallingRef
import munit.CatsEffectSuite
import org.http4s.implicits._
import org.http4s.Method
import org.http4s.Request
import org.http4s.ServerSentEvent
import scala.concurrent.duration._

class DashboardRoutesSuite extends CatsEffectSuite {

  private val stats = PipelineStats(80, 12, 40, Some(QueueDepth(693, 50)), 50)

  private def eventsFrom(latest: SignallingRef[IO, Option[PipelineStats]]): fs2.Stream[IO, ServerSentEvent] =
    fs2.Stream
      .eval(Ref[IO].of(ProcessingDelay.None))
      .flatMap { knob =>
        val app = new RoutesImpl[IO](knob, latest).publicRoutes.orNotFound
        fs2.Stream.eval(app.run(Request[IO](Method.GET, uri"/dashboard/events")))
      }
      .flatMap(_.body.through(ServerSentEvent.decoder[IO]))

  test("events_statsAvailable_sendsThemAsAStatsEvent") {
    // Arrange
    val result = for {
      latest <- SignallingRef[IO].of(Option(stats))
      // Act
      events <- eventsFrom(latest).take(1).compile.toList
    } yield events

    // Assert
    result.map { events =>
      assertEquals(events.map(_.eventType), List(Some("stats")))
      assertEquals(
        events.flatMap(_.data),
        List("""{"processedPerSec":80,"delayAvgMillis":12,"delayMaxMillis":40,"queue":{"ready":693,"unacked":50},"knobDelayMillis":50}""")
      )
    }
  }

  test("events_noStatsYet_sendsNothingUntilTheFirstStats") {
    // Arrange
    val program = for {
      latest <- SignallingRef[IO].of(Option.empty[PipelineStats])
      // Act: the 1st stats arrive after 3 s
      _ <- (IO.sleep(3.seconds) >> latest.set(Some(stats))).start
      first <- eventsFrom(latest).take(1).compile.toList.timed
    } yield first

    // Assert
    TestControl.executeEmbed(program).map { case (elapsed, events) =>
      assertEquals(elapsed, 3.seconds, "the initial None must not be sent")
      assertEquals(events.size, 1)
    }
  }

}
