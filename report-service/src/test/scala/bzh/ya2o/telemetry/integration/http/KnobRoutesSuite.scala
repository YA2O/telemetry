package bzh.ya2o.telemetry
package integration.http

import bzh.ya2o.telemetry.logic.PipelineStats
import bzh.ya2o.telemetry.logic.ProcessingDelay
import bzh.ya2o.telemetry.logic.ReportService.Report
import cats.effect.IO
import cats.effect.Ref
import fs2.concurrent.Signal
import munit.CatsEffectSuite
import org.http4s.implicits._
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status

class KnobRoutesSuite extends CatsEffectSuite {

  private def routesWith(delay: Ref[IO, ProcessingDelay]) =
    new RoutesImpl[IO](
      delay,
      Signal.constant[IO, Option[PipelineStats]](None),
      Signal.constant[IO, Option[Report]](None)
    ).publicRoutes.orNotFound

  private def put(body: String) =
    Request[IO](Method.PUT, uri"/knob/delay").withEntity(body)

  private val get = Request[IO](Method.GET, uri"/knob/delay")

  test("getDelay_default_returns0") {
    // Arrange
    val response = for {
      delay <- Ref[IO].of(ProcessingDelay.None)
      // Act
      resp <- routesWith(delay).run(get)
      body <- resp.as[String]
    } yield (resp.status, body)

    // Assert
    response.map(result => assertEquals(result, (Status.Ok, """{"millis":0}""")))
  }

  test("putDelay_50_setsTheKnobAndGetReturnsIt") {
    // Arrange
    val result = for {
      delay <- Ref[IO].of(ProcessingDelay.None)
      app = routesWith(delay)
      // Act
      putResp <- app.run(put("""{"millis": 50}"""))
      getBody <- app.run(get).flatMap(_.as[String])
      stored <- delay.get
    } yield (putResp.status, getBody, stored.millis)

    // Assert
    result.map(r => assertEquals(r, (Status.Ok, """{"millis":50}""", 50L)))
  }

  test("putDelay_invalidBodies_return400AndLeaveTheKnobUnchanged") {
    // Arrange
    val invalidBodies = List(
      """{"millis": -5}""",
      """{"millis": 60001}""",
      """{"millis": "abc"}""",
      """{"seconds": 1}""",
      "not json"
    )

    invalidBodies.foldLeft(IO.unit) { (previous, body) =>
      previous >> (for {
        delay <- Ref[IO].of(ProcessingDelay.None)
        // Act
        resp <- routesWith(delay).run(put(body))
        stored <- delay.get
      } yield {
        // Assert
        assertEquals(resp.status, Status.BadRequest, s"body = [$body]")
        assertEquals(stored, ProcessingDelay.None, s"body = [$body]")
      })
    }
  }

}
