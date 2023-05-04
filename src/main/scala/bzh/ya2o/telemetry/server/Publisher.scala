package bzh.ya2o.telemetry
package server

import bzh.ya2o.telemetry.logging.Logger
import bzh.ya2o.telemetry.model.Measurement
import bzh.ya2o.telemetry.streamingmiddleware.StreamingMiddlewarePublisher
import cats.effect.Sync
import cats.implicits._

trait Publisher[F[_]] {
  def publish(data: Measurement): F[Unit]
}

class PublisherImpl[F[_]](
  streaming: StreamingMiddlewarePublisher[F, Measurement],
  logger: Logger[F]
)(
  implicit F: Sync[F]
) extends Publisher[F] {

  override def publish(data: Measurement): F[Unit] = {
    logger.debug(s">>> (publishing) $data") >> streaming.publish(data)
  }

}
