package bzh.ya2o.telemetry
package application.config

import bzh.ya2o.telemetry.application.config.MessagingConfig.PositiveInt
import bzh.ya2o.telemetry.application.config.MessagingConfig.SimpleString
import cats.implicits._
import ciris._
import ciris.refined._
import ciris.ConfigValue
import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric._
import eu.timepit.refined.string.MatchesRegex
import eu.timepit.refined.types.net.PortNumber
import eu.timepit.refined.types.string.NonEmptyString

final case class MessagingConfig(
  host: NonEmptyString,
  portNumber: PortNumber,
  userName: SimpleString,
  password: SimpleString, // TODO use Secret string
  maxQueued: PositiveInt,
  prefetchCount: PositiveInt,
  exchangeName: SimpleString,
  routingKey: SimpleString,
  queueName: SimpleString
)

object MessagingConfig {
  type SimpleString = String Refined MatchesRegex[W.`"""[a-zA-Z_\\.]+"""`.T]
  type PositiveInt = Int Refined Positive

  def config[F[_]]: ConfigValue[F, MessagingConfig] =
    (
      env("RABBITMQ_HOST").as[NonEmptyString].default("localhost"),
      prop("messaging.port").as[PortNumber].default(5672),
      prop("messaging.user").as[SimpleString].default("admin"),
      prop("messaging.password").as[SimpleString].default("admin"),
      prop("messaging.maxQueued").as[PositiveInt].default(100),
      prop("messaging.prefetchCount").as[PositiveInt].default(50),
      prop("messaging.exchangeName").as[SimpleString].default("telemetry.exchange"),
      prop("messaging.routingKey").as[SimpleString].default("telemetry.measurement"),
      prop("messaging.queueName").as[SimpleString].default("telemetry.queue")
    ).mapN(MessagingConfig.apply)

}
