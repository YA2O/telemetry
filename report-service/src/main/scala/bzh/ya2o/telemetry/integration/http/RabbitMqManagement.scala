package bzh.ya2o.telemetry
package integration.http

import bzh.ya2o.telemetry.application.config.MessagingConfig
import bzh.ya2o.telemetry.logic.QueueDepth
import cats.effect.Concurrent
import io.circe.Decoder
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.http4s.BasicCredentials
import org.http4s.Method
import org.http4s.Request
import org.http4s.Uri

trait QueueDepthSource[F[_]] {
  def queueDepth: F[QueueDepth]
}

/** Reads the queue depth from RabbitMQ's management HTTP API.
  * Fails (raises) if RabbitMQ answers with an error or without the queue figures.
  */
class RabbitMqManagement[F[_]](client: Client[F], config: MessagingConfig)(implicit F: Concurrent[F])
  extends QueueDepthSource[F] {

  private[this] val request: Request[F] = {
    val uri = Uri(
      scheme = Some(Uri.Scheme.http),
      authority = Some(Uri.Authority(host = Uri.RegName(config.host.value), port = Some(config.managementPort.value)))
    ) / "api" / "queues" / "/" / config.queueName.value
    Request[F](Method.GET, uri)
      .putHeaders(Authorization(BasicCredentials(config.userName.value, config.password.value)))
  }

  override def queueDepth: F[QueueDepth] =
    client.expect[QueueDepth](request)(jsonOf[F, QueueDepth](F, RabbitMqManagement.queueDepthDecoder))

}

object RabbitMqManagement {
  val queueDepthDecoder: Decoder[QueueDepth] =
    Decoder.forProduct2("messages_ready", "messages_unacknowledged")(QueueDepth.apply)
}
