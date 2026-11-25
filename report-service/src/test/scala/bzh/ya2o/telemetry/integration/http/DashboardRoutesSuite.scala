package bzh.ya2o.telemetry
package integration.http

import bzh.ya2o.telemetry.logic.PipelineStats
import bzh.ya2o.telemetry.logic.ProcessingDelay
import bzh.ya2o.telemetry.logic.QueueDepth
import bzh.ya2o.telemetry.logic.ReportService.Report
import bzh.ya2o.telemetry.logic.ReportService.VersionCounter
import cats.effect.testkit.TestControl
import cats.effect.IO
import cats.effect.Ref
import fs2.concurrent.Signal
import fs2.concurrent.SignallingRef
import java.time.Instant
import scala.collection.SortedMap
import munit.CatsEffectSuite
import org.http4s.implicits._
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.Request
import org.http4s.ServerSentEvent
import org.http4s.Status
import scala.concurrent.duration._

class DashboardRoutesSuite extends CatsEffectSuite {

  private val stats = PipelineStats(80, 12, 40, Some(QueueDepth(693, 50)), 50)

  private def eventsFrom(
    latest: SignallingRef[IO, Option[PipelineStats]],
    latestReport: Signal[IO, Option[Report]] = Signal.constant[IO, Option[Report]](None)
  ): fs2.Stream[IO, ServerSentEvent] =
    fs2.Stream
      .eval(Ref[IO].of(ProcessingDelay.None))
      .flatMap { knob =>
        val app = new RoutesImpl[IO](knob, latest, latestReport).publicRoutes.orNotFound
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

  test("events_statsAndReportAvailable_sendsOneEventOfEachType") {
    // Arrange
    val report = Report(
      Instant.parse("2026-11-25T12:00:00Z"),
      10.seconds,
      List(VersionCounter(TestData.measurement("v1.0.0", 1f).clientVersion, 3, SortedMap(0 -> 1L, 10 -> 2L)))
    )
    val result = for {
      latestStats <- SignallingRef[IO].of(Option(stats))
      latestReport <- SignallingRef[IO].of(Option(report))
      // Act
      events <- eventsFrom(latestStats, latestReport).take(2).compile.toList
    } yield events

    // Assert
    result.map { events =>
      assertEquals(events.flatMap(_.eventType).toSet, Set("stats", "report"))
      assertEquals(
        events.find(_.eventType.contains("report")).flatMap(_.data),
        Some("""{"timestamp":"2026-11-25T12:00:00Z","period":"10 seconds","counters":[{"clientVersion":"v1.0.0","count":3,"countPerDivision":{"0":1,"10":2}}]}""")
      )
    }
  }

  test("page_get_servesTheDashboardHtml") {
    // Arrange
    val response = for {
      knob <- Ref[IO].of(ProcessingDelay.None)
      latest <- SignallingRef[IO].of(Option.empty[PipelineStats])
      app = new RoutesImpl[IO](knob, latest, Signal.constant[IO, Option[Report]](None)).publicRoutes.orNotFound
      // Act
      resp <- app.run(Request[IO](Method.GET, uri"/dashboard"))
      body <- resp.as[String]
    } yield (resp, body)

    // Assert
    response.map { case (resp, body) =>
      assertEquals(resp.status, Status.Ok)
      assertEquals(resp.contentType.map(_.mediaType), Some(MediaType.text.html))
      assert(body.contains("""new EventSource("/dashboard/events")"""), "page must read the event stream")
    }
  }

}
