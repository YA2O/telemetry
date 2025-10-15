package bzh.ya2o.telemetry
package integration.messaging.middleware

import fs2.Stream
import scala.{Stream => _}

trait MessagingSubscriber[F[_], A] {
  def subscribe(): Stream[F, A]
}
