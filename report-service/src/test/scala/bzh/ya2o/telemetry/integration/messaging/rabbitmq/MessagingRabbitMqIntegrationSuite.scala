package bzh.ya2o.telemetry
package integration.messaging.rabbitmq

import bzh.ya2o.telemetry.application.config.MessagingConfig
import bzh.ya2o.telemetry.application.config.MessagingConfig.PositiveInt
import bzh.ya2o.telemetry.application.config.MessagingConfig.SimpleString
import bzh.ya2o.telemetry.integration.http.RabbitMqManagement
import bzh.ya2o.telemetry.integration.json.JsonDecoders._
import bzh.ya2o.telemetry.integration.json.JsonEncoders._
import bzh.ya2o.telemetry.logic.QueueDepth
import bzh.ya2o.telemetry.model.CpuMeasurement
import bzh.ya2o.telemetry.TestData.NoOpLogger
import cats.effect.IO
import cats.effect.Resource
import com.rabbitmq.client.Channel
import com.rabbitmq.client.ConnectionFactory
import eu.timepit.refined.auto._
import eu.timepit.refined.types.net.PortNumber
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.syntax._
import java.nio.charset.StandardCharsets.UTF_8
import munit.CatsEffectSuite
import org.http4s.ember.client.EmberClientBuilder
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.utility.DockerImageName
import scala.concurrent.duration._

/** Runs against a real RabbitMQ in Docker: acks, rejects, prefetch and redelivery only exist there.
  *
  * Each test uses its own queue (bound to the shared exchange with the queue name as routing key),
  * so tests do not see each other's messages.
  */
class MessagingRabbitMqIntegrationSuite extends CatsEffectSuite {

  private val ExchangeName: SimpleString = "test.exchange"

  private val rabbit = ResourceSuiteLocalFixture(
    "rabbitmq",
    Resource.make(IO.blocking {
      val container = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management"))
      container.start()
      container
    })(container => IO.blocking(container.stop()))
  )

  override def munitFixtures = List(rabbit)

  test("subscribe_validMessageAcked_isEmittedAndNotRedelivered") {
    // Arrange
    val queue: SimpleString = "test.valid"
    val expected = TestData.measurement("v1.0.0", 42f)

    // Act
    val received = messaging(queue, prefetchCount = 10).use { m =>
      publishRaw(queue, expected.asJson.noSpaces) >>
        m.subscribe().take(1).evalTap(_.ack).map(_.value).compile.toList.timeout(10.seconds)
    }

    // Assert
    for {
      values <- received
      ready <- readyCount(queue)
    } yield {
      assertEquals(values, List(expected))
      assertEquals(ready, 0, "an acked message must not return to the queue")
    }
  }

  test("subscribe_invalidMessage_isRejectedNotRedeliveredAndStreamContinues") {
    // Arrange
    val queue: SimpleString = "test.invalid"
    val valid = TestData.measurement("v1.0.0", 10f)
    val invalidBodies = List("not json", """{"deviceId":"","cpu":500}""")

    // Act
    val received = messaging(queue, prefetchCount = 10).use { m =>
      invalidBodies.foldLeft(IO.unit)((acc, body) => acc >> publishRaw(queue, body)) >>
        publishRaw(queue, valid.asJson.noSpaces) >>
        m.subscribe().take(1).evalTap(_.ack).map(_.value).compile.toList.timeout(10.seconds)
    }

    // Assert
    for {
      values <- received
      ready <- readyCount(queue)
    } yield {
      assertEquals(values, List(valid), "only the valid message is emitted")
      assertEquals(ready, 0, "rejected messages must not be requeued")
    }
  }

  test("subscribe_prefetch3NoAcks_deliversAtMost3AndLeavesTheRestReady") {
    // Arrange
    val queue: SimpleString = "test.prefetch"
    val published = 8
    val body = TestData.measurement("v1.0.0", 50f).asJson.noSpaces

    // Act
    val result = messaging(queue, prefetchCount = 3).use { m =>
      List.fill(published)(publishRaw(queue, body)).foldLeft(IO.unit)(_ >> _) >>
        m.subscribe().interruptAfter(2.seconds).compile.count.flatMap { delivered =>
          readyCount(queue).map(ready => (delivered, ready))
        }
    }

    // Assert
    for {
      (delivered, readyWhileConsuming) <- result
      readyAfterClose <- readyCount(queue)
    } yield {
      assertEquals(delivered, 3L, "prefetch must cap unacked deliveries")
      assertEquals(readyWhileConsuming, published - 3, "the backlog must stay in RabbitMQ")
      assertEquals(readyAfterClose, published, "unacked messages return to the queue on close")
    }
  }

  test("subscribe_closedBeforeAck_redeliversTheMessage") {
    // Arrange
    val queue: SimpleString = "test.redelivery"
    val expected = TestData.measurement("v2.0.0", 77f)

    // Act
    val firstRun = messaging(queue, prefetchCount = 10).use { m =>
      publishRaw(queue, expected.asJson.noSpaces) >>
        m.subscribe().take(1).map(_.value).compile.toList.timeout(10.seconds)
    }
    val secondRun = messaging(queue, prefetchCount = 10).use { m =>
      m.subscribe().take(1).evalTap(_.ack).map(_.value).compile.toList.timeout(10.seconds)
    }

    // Assert
    for {
      first <- firstRun
      second <- secondRun
      ready <- readyCount(queue)
    } yield {
      assertEquals(first, List(expected))
      assertEquals(second, List(expected), "a message not acked before close must be delivered again")
      assertEquals(ready, 0)
    }
  }

  test("queueDepth_realManagementApi_reportsTheReadyMessages") {
    // Arrange
    val queue: SimpleString = "test.depth"
    val body = TestData.measurement("v1.0.0", 30f).asJson.noSpaces
    val expected = QueueDepth(ready = 2, unacked = 0)

    // Act: RabbitMQ refreshes these figures every 5 s by default, so poll for up to 20 s
    val result = messaging(queue, prefetchCount = 10).use { m =>
      publishRaw(queue, body) >> publishRaw(queue, body) >>
        EmberClientBuilder.default[IO].build.use { client =>
          val api = new RabbitMqManagement[IO](client, configFor(queue, prefetchCount = 10))
          def poll(attemptsLeft: Int): IO[Either[Throwable, QueueDepth]] =
            api.queueDepth.attempt.flatMap {
              case Right(`expected`) => IO.pure(Right(expected))
              case other if attemptsLeft <= 1 => IO.pure(other)
              case _ => IO.sleep(1.second) >> poll(attemptsLeft - 1)
            }
          poll(20)
        }
    }

    // Assert
    result.map(depth => assertEquals(depth, Right(expected)))
  }

  private def messaging(
    queue: SimpleString,
    prefetchCount: PositiveInt
  ): Resource[IO, MessagingRabbitMq[IO, CpuMeasurement]] =
    MessagingRabbitMq.make[IO, CpuMeasurement](configFor(queue, prefetchCount), NoOpLogger)

  private def configFor(queue: SimpleString, prefetchCount: PositiveInt): MessagingConfig = {
    val container = rabbit()
    MessagingConfig(
      host = NonEmptyString.unsafeFrom(container.getHost),
      portNumber = PortNumber.unsafeFrom(container.getAmqpPort),
      managementPort = PortNumber.unsafeFrom(container.getHttpPort),
      userName = "guest",
      password = "guest",
      maxQueued = 100,
      prefetchCount = prefetchCount,
      exchangeName = ExchangeName,
      routingKey = queue,
      queueName = queue
    )
  }

  private def publishRaw(routingKey: SimpleString, body: String): IO[Unit] =
    withChannel(_.basicPublish(ExchangeName.value, routingKey.value, null, body.getBytes(UTF_8)))

  /** Messages waiting in the queue, not counting those delivered and unacked. */
  private def readyCount(queue: SimpleString): IO[Int] =
    withChannel(_.queueDeclarePassive(queue.value).getMessageCount)

  private def withChannel[A](f: Channel => A): IO[A] = IO.blocking {
    val container = rabbit()
    val factory = new ConnectionFactory()
    factory.setHost(container.getHost)
    factory.setPort(container.getAmqpPort)
    factory.setUsername(container.getAdminUsername)
    factory.setPassword(container.getAdminPassword)
    val connection = factory.newConnection()
    try f(connection.createChannel())
    finally connection.close()
  }

}
