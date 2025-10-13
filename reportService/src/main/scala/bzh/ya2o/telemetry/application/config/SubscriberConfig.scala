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

final case class MessagingConfig(
  publisher: PublisherConfig,
  subscriber: SubscriberConfig
)

object MessagingConfig {
  type SimpleString = String Refined MatchesRegex[W.`"""[a-zA-Z_\\.]+"""`.T]
  type PositiveInt = Int Refined Positive

  def config[F[_]]: ConfigValue[F, MessagingConfig] = (
    PublisherConfig.config,
    SubscriberConfig.config
  ).parMapN(MessagingConfig.apply)
}

final case class PublisherConfig(
  exchangeName: SimpleString,
  routingKey: SimpleString
)

object PublisherConfig {
  def config[F[_]]: ConfigValue[F, PublisherConfig] =
    (
      prop("publisher.exchangeName").as[SimpleString].default("telemetry.exchange"),
      prop("publisher.routingKey").as[SimpleString].default("telemetry.measurement")
    ).mapN(PublisherConfig.apply)
}

final case class SubscriberConfig(
  exchangeName: SimpleString,
  routingKey: SimpleString,
  queueName: SimpleString,
  maxQueued: PositiveInt
)

object SubscriberConfig {
  def config[F[_]]: ConfigValue[F, SubscriberConfig] =
    (
      prop("publisher.exchangeName").as[SimpleString].default("telemetry.exchange"),
      prop("publisher.routingKey").as[SimpleString].default("telemetry.measurement"),
      prop("publisher.queueName").as[SimpleString].default("telemetry.queue"),
      prop("subscriber.maxQueued").as[PositiveInt].default(100)
    ).mapN(SubscriberConfig.apply)
}
