package bzh.ya2o.telemetry
package integration.messaging

import bzh.ya2o.telemetry.application.logging.Logger
import bzh.ya2o.telemetry.integration.messaging.middleware.MessagingPublisher
import bzh.ya2o.telemetry.model.CpuMeasurement
import cats.effect.Sync
import cats.implicits._

trait Publisher[F[_]] {
  def publish(data: CpuMeasurement): F[Unit]
}

class PublisherImpl[F[_]](
  streaming: MessagingPublisher[F, CpuMeasurement],
  logger: Logger[F]
)(
  implicit F: Sync[F]
) extends Publisher[F] {

  override def publish(data: CpuMeasurement): F[Unit] = {
    logger.debug(s">>> (publishing) $data") >> streaming.publish(data)
  }

}
