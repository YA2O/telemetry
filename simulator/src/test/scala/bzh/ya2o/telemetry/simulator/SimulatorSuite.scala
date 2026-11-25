package bzh.ya2o.telemetry
package simulator

import bzh.ya2o.telemetry.application.logging.Logger
import bzh.ya2o.telemetry.integration.json.JsonDecoders._
import bzh.ya2o.telemetry.model.CpuMeasurement
import cats.effect.std.Random
import cats.effect.testkit.TestControl
import cats.effect.IO
import cats.effect.Ref
import cats.implicits._
import eu.timepit.refined.auto._
import io.circe.parser.decode
import java.time.Instant
import munit.CatsEffectSuite
import org.http4s.client.Client
import org.http4s.HttpApp
import org.http4s.Response
import org.http4s.Status
import org.http4s.Uri
import scala.concurrent.duration._

class SimulatorSuite extends CatsEffectSuite {

  private val Target = Uri.unsafeFromString("http://test/telemetry/cpu")

  private object NoOpLogger extends Logger[IO] {
    override def error(msg: String): IO[Unit] = IO.unit
    override def warn(msg: String): IO[Unit] = IO.unit
    override def info(msg: String): IO[Unit] = IO.unit
    override def debug(msg: String): IO[Unit] = IO.unit
  }

  /** Runs the simulator for just over 1 s of fake time; returns what the fake server received. */
  private def runFor1Second(
    ratePerSec: SimulatorConfig.PositiveInt,
    respond: Status
  ): IO[(List[Status], List[CpuMeasurement])] =
    TestControl.executeEmbed(
      for {
        received <- Ref[IO].of(List.empty[CpuMeasurement])
        app = HttpApp[IO] { request =>
          request.as[String].flatMap { body =>
            IO.fromEither(decode[CpuMeasurement](body)).flatMap(m => received.update(m :: _))
          } >> IO.pure(Response[IO](respond))
        }
        random <- Random.scalaUtilRandomSeedInt[IO](42)
        generator <- MeasurementGenerator.make[IO](random)
        config = SimulatorConfig(Target, ratePerSec, concurrency = 4)
        simulator = new Simulator[IO](Client.fromHttpApp(app), generator, config, NoOpLogger)
        statuses <- simulator.run.interruptAfter(1050.millis).compile.toList
        measurements <- received.get
      } yield (statuses, measurements)
    )

  test("run_rate10PerSecFor1Second_sends10MeasurementsStampedWithTheSendTime") {
    // Arrange
    val expectedTimestamps = (1 to 10).map(i => Instant.ofEpochMilli(i * 100L)).toList

    // Act
    val result = runFor1Second(ratePerSec = 10, respond = Status.Accepted)

    // Assert
    result.map { case (statuses, measurements) =>
      assertEquals(statuses, List.fill(10)(Status.Accepted))
      assertEquals(measurements.map(_.timestamp).sorted, expectedTimestamps)
    }
  }

  test("run_serverAnswers500_keepsSendingAtTheSameRate") {
    // Arrange
    val respond = Status.InternalServerError

    // Act
    val result = runFor1Second(ratePerSec = 10, respond = respond)

    // Assert
    result.map { case (statuses, measurements) =>
      assertEquals(statuses, List.fill(10)(respond))
      assertEquals(measurements.size, 10)
    }
  }

  test("run_clientThrows_logsAndKeepsSending") {
    // Arrange
    val failingClient = Client[IO](_ => cats.effect.Resource.raiseError[IO, Response[IO], Throwable](new RuntimeException("down")))
    val program = for {
      random <- Random.scalaUtilRandomSeedInt[IO](42)
      generator <- MeasurementGenerator.make[IO](random)
      config = SimulatorConfig(Target, ratePerSec = 10, concurrency = 4)
      simulator = new Simulator[IO](failingClient, generator, config, NoOpLogger)
      // Act
      statuses <- simulator.run.interruptAfter(1050.millis).compile.toList
    } yield statuses

    // Assert
    TestControl.executeEmbed(program).map(statuses => assertEquals(statuses.size, 10))
  }

}
