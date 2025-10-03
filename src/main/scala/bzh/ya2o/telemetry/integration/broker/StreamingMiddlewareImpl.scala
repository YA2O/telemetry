package bzh.ya2o.telemetry
package integration.broker

import bzh.ya2o.telemetry.application.config.MiddlewareConfig
import cats.effect.Async
import cats.effect.Sync
import cats.implicits._
import fs2.concurrent.Topic
import fs2.Stream
import scala.{Stream => _}

class StreamingMiddlewareImpl[F[_], A](topic: Topic[F, A], config: MiddlewareConfig)(implicit F: Sync[F])
  extends StreamingMiddlewarePublisher[F, A]
  with StreamingMiddlewareSubscriber[F, A] {

  override def publish(a: A): F[Unit] = topic.publish1(a).flatMap {
    _.fold(
      _ => F.raiseError(new RuntimeException("Topic is closed!")),
      F.pure
    )
  }

  override def subscribe(): Stream[F, A] = topic.subscribe(config.maxQueued.value)
}

object StreamingMiddlewareImpl {
  def make[F[_], A](config: MiddlewareConfig)(implicit F: Async[F]): F[StreamingMiddlewareImpl[F, A]] = {
    Topic[F, A]
      .map(new StreamingMiddlewareImpl[F, A](_, config))
  }
}
