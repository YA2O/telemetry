package bzh.ya2o.telemetry.integration.messaging

import bzh.ya2o.telemetry.integration.messaging.middleware.MessagingSubscriber
import bzh.ya2o.telemetry.model.CpuMeasurement
import fs2.Stream

trait Subscriber[F[_]] {
  def subscribe(): Stream[F, CpuMeasurement]
}

class SubscriberImpl[F[_]](
  streaming: MessagingSubscriber[F, CpuMeasurement]
) extends Subscriber[F] {

  override def subscribe(): Stream[F, CpuMeasurement] = {
    streaming.subscribe()
  }

}
