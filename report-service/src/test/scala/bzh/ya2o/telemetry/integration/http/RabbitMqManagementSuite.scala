package bzh.ya2o.telemetry
package integration.http

import bzh.ya2o.telemetry.application.config.MessagingConfig
import bzh.ya2o.telemetry.logic.QueueDepth
import cats.effect.IO
import cats.effect.Ref
import eu.timepit.refined.auto._
import eu.timepit.refined.types.net.PortNumber
import eu.timepit.refined.types.string.NonEmptyString
import munit.CatsEffectSuite
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.http4s.HttpApp
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status

class RabbitMqManagementSuite extends CatsEffectSuite {

  private val config = MessagingConfig(
    host = NonEmptyString.unsafeFrom("rabbit.test"),
    portNumber = PortNumber.unsafeFrom(5672),
    managementPort = PortNumber.unsafeFrom(15672),
    userName = "admin",
    password = "secret",
    maxQueued = 100,
    prefetchCount = 50,
    exchangeName = "telemetry.exchange",
    routingKey = "telemetry.measurement",
    queueName = "telemetry.queue"
  )

  /** A management API that answers `status` with `body`, and records the request it got. */
  private def management(status: Status, body: String): IO[(RabbitMqManagement[IO], Ref[IO, Option[Request[IO]]])] =
    Ref[IO].of(Option.empty[Request[IO]]).map { seen =>
      val app = HttpApp[IO](request => seen.set(Some(request)).as(Response[IO](status).withEntity(body)))
      (new RabbitMqManagement[IO](Client.fromHttpApp(app), config), seen)
    }

  test("queueDepth_okAnswer_readsReadyAndUnackedFromTheQueueEndpoint") {
    // Arrange
    val answer = """{"name":"telemetry.queue","messages_ready":693,"messages_unacknowledged":50,"consumers":1}"""

    val result = for {
      (api, seen) <- management(Status.Ok, answer)
      // Act
      depth <- api.queueDepth
      request <- seen.get
    } yield (depth, request)

    // Assert
    result.map { case (depth, request) =>
      assertEquals(depth, QueueDepth(ready = 693, unacked = 50))
      assertEquals(
        request.map(_.uri.renderString),
        Some("http://rabbit.test:15672/api/queues/%2F/telemetry.queue")
      )
      assert(request.exists(_.headers.get[Authorization].isDefined), "basic auth header missing")
    }
  }

  test("queueDepth_badAnswers_fail") {
    // Arrange
    val cases = List(
      Status.NotFound -> """{"error":"Object Not Found"}""",
      Status.Ok -> """{"name":"telemetry.queue"}""", // figures not collected yet
      Status.Ok -> "not json"
    )

    cases.foldLeft(IO.unit) { case (previous, (status, body)) =>
      previous >> management(status, body).flatMap { case (api, _) =>
        // Act
        api.queueDepth.attempt.map { result =>
          // Assert
          assert(result.isLeft, s"expected a failure for [$status] [$body], got [$result]")
        }
      }
    }
  }

}
