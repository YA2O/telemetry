package bzh.ya2o.telemetry
package simulator

import bzh.ya2o.telemetry.application.logging.Logger
import bzh.ya2o.telemetry.integration.json.JsonEncoders._
import cats.effect.Temporal
import cats.implicits._
import fs2.Stream
import io.circe.syntax._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.client.Client
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import scala.concurrent.duration._

/** Sends 1 measurement every `1 s / ratePerSec`, with at most `concurrency` requests in flight.
  * A failed request is logged and does not stop the simulator.
  */
class Simulator[F[_]](
  client: Client[F],
  generator: MeasurementGenerator[F],
  config: SimulatorConfig,
  logger: Logger[F]
)(
  implicit F: Temporal[F]
) {

  def run: Stream[F, Status] = {
    val period = 1.second / config.ratePerSec.value.toLong
    Stream
      .awakeEvery[F](period)
      .evalMap(_ => generator.next)
      .parEvalMapUnordered(config.concurrency.value) { measurement =>
        val request = Request[F](Method.POST, config.target).withEntity(measurement.asJson)
        client
          .status(request)
          .flatTap(status => logger.warn(s"Request rejected: [$status]").unlessA(status.isSuccess))
          .handleErrorWith(error => logger.warn(s"Request failed: [$error]").as(Status.ServiceUnavailable))
      }
  }

}
