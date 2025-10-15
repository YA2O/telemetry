package bzh.ya2o.telemetry
package integration.messaging.rabbitmq

import bzh.ya2o.telemetry.application.config.MessagingConfig
import bzh.ya2o.telemetry.application.error.CustomError.TheirServerError
import bzh.ya2o.telemetry.application.logging.Logger
import bzh.ya2o.telemetry.integration.messaging.middleware.MessagingPublisher
import bzh.ya2o.telemetry.integration.messaging.middleware.MessagingSubscriber
import cats.data.Validated
import cats.effect.std.Dispatcher
import cats.effect.Async
import cats.effect.Resource
import cats.effect.Sync
import cats.implicits._
import dev.profunktor.fs2rabbit.config.declaration.DeclarationQueueConfig
import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model._
import dev.profunktor.fs2rabbit.model.ExchangeType
import fs2.Stream
import io.circe.parser.decodeAccumulating
import io.circe.syntax._
import io.circe.Decoder
import io.circe.Encoder
import scala.concurrent.duration._

class MessagingRabbitMq[F[_], A: Encoder: Decoder](
  pub: String => F[Unit],
  sub: Stream[F, String],
  logger: Logger[F]
)(
  implicit F: Sync[F]
) extends MessagingPublisher[F, A]
  with MessagingSubscriber[F, A] {

  override def publish(a: A): F[Unit] = {
    F.delay(logger.debug(s"Publishing $a to RabbitMQ")) >> pub(a.asJson.deepDropNullValues.noSpaces)
  }

  override def subscribe(): Stream[F, A] = {
    for {
      msg <- sub
    } yield {
      decodeAccumulating(msg) match {
        case Validated.Valid(a) => a
        case Validated.Invalid(err) =>
          throw TheirServerError(s"Failed to decode message from RabbitMQ: [$err]")
      }
    }
  }

}

object MessagingRabbitMq {

  def make[F[_], A: Encoder: Decoder](
    config: MessagingConfig,
    logger: Logger[F]
  )(
    implicit F: Async[F]
  ): Resource[F, MessagingRabbitMq[F, A]] = {

    val rabbitConfig = Fs2RabbitConfig(
      virtualHost = "/",
      host = "host.docker.internal", // TODO: use localhost for local dev
      username = Some(config.userName.value),
      password = Some(config.password.value),
      port = config.portNumber.value,
      ssl = false,
      connectionTimeout = 3.seconds,
      requeueOnNack = false,
      requeueOnReject = false,
      automaticTopologyRecovery = true,
      internalQueueSize = Some(config.maxQueued.value)
    )

    val exchangeName = ExchangeName(config.exchangeName.value)
    val routingKey = RoutingKey(config.routingKey.value)
    val queueName = QueueName(config.queueName.value)

    for {
      dispatcher <- Dispatcher.parallel[F]
      client <- Resource.eval(RabbitClient.default[F](rabbitConfig).build(dispatcher))
      conn <- client.createConnection
      channel <- client.createChannel(conn)
      messaging <- {
        implicit val ch: AMQPChannel = channel
        for {
          _ <- Resource.eval(client.declareQueue(DeclarationQueueConfig.classic(queueName)))
          _ <- Resource.eval(client.declareExchange(exchangeName, ExchangeType.Direct))
          _ <- Resource.eval(client.bindQueue(queueName, exchangeName, routingKey))
          pub <- Resource.eval(client.createPublisher[String](exchangeName, routingKey))
          sub <- Resource.eval(client.createAutoAckConsumer[String](queueName))
          _ <- Resource.eval(logger.info("RabbitMQ messaging initialized"))
        } yield new MessagingRabbitMq[F, A](pub, sub.map(_.payload), logger)
      }
    } yield messaging
  }

}
