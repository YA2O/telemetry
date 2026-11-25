package bzh.ya2o.telemetry
package simulator

import bzh.ya2o.telemetry.application.logging.LoggerImpl
import cats.effect.std.Random
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import org.http4s.ember.client.EmberClientBuilder
import org.log4s.getLogger

object Main extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    val logger = new LoggerImpl[IO](getLogger)

    val resources = for {
      config <- SimulatorConfig.config[IO].resource
      client <- EmberClientBuilder.default[IO].build
    } yield (config, client)

    resources
      .use { case (config, client) =>
        for {
          random <- Random.scalaUtilRandom[IO]
          generator <- MeasurementGenerator.make[IO](random)
          _ <- logger.info(s"Sending ${config.ratePerSec} measurements/s to ${config.target}")
          _ <- new Simulator[IO](client, generator, config, logger).run.compile.drain
        } yield ExitCode.Success
      }
      .handleErrorWith(error => logger.error(s"Simulator failed: $error").as(ExitCode.Error))
  }

}
