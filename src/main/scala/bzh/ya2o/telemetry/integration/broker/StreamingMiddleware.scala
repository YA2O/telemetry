package bzh.ya2o.telemetry
package integration.broker

import fs2.Stream
import scala.{Stream => _}

trait StreamingMiddlewarePublisher[F[_], A] {
  def publish(a: A): F[Unit]
}

trait StreamingMiddlewareSubscriber[F[_], A] {
  def subscribe(): Stream[F, A]
}
