package bzh.ya2o.telemetry
package integration.messaging.middleware

/** A message that decoded successfully, with the action that acknowledges it to the broker.
  * Call `ack` once the message has been fully processed, never before.
  */
final case class Delivery[F[_], A](value: A, ack: F[Unit])
