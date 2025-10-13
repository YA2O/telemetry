package bzh.ya2o.telemetry
package integration.messaging.middleware

trait MessagingPublisher[F[_], A] {
  def publish(a: A): F[Unit]
}
