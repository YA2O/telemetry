package bzh.ya2o.telemetry
package integration.messaging.middleware

import bzh.ya2o.telemetry.application.config.SubscriberConfig
import cats.effect.Async
import cats.effect.Sync
import cats.implicits._
import fs2.concurrent.Topic
import fs2.Stream
import scala.{Stream => _}

class MessagingInMemory[F[_], A](topic: Topic[F, A], config: SubscriberConfig)(implicit F: Sync[F])
  extends MessagingPublisher[F, A]
  with MessagingSubscriber[F, A] {

  override def publish(a: A): F[Unit] = topic.publish1(a) >>= {
    case Left(_) => F.raiseError(new RuntimeException("Topic is closed!"))
    case Right(_) => F.unit
  }

  override def subscribe(): Stream[F, A] = topic.subscribe(config.maxQueued.value)
}

object MessagingInMemory {
  def make[F[_], A](config: SubscriberConfig)(implicit F: Async[F]): F[MessagingInMemory[F, A]] = {
    Topic[F, A]
      .map(new MessagingInMemory[F, A](_, config))
  }
}
