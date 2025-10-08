package bzh.ya2o.telemetry

import bzh.ya2o.telemetry.application.config.Config
import bzh.ya2o.telemetry.application.logging.LoggerImpl
import bzh.ya2o.telemetry.application.Server
import bzh.ya2o.telemetry.integration.http.RoutesImpl
import bzh.ya2o.telemetry.integration.json.JsonDecoders._
import bzh.ya2o.telemetry.integration.json.JsonEncoders._
import bzh.ya2o.telemetry.integration.messaging.rabbitmq.MessagingRabbitMq
import bzh.ya2o.telemetry.integration.messaging.PublisherImpl
import bzh.ya2o.telemetry.integration.messaging.SubscriberImpl
import bzh.ya2o.telemetry.logic.ReportServiceImpl
import cats.effect.kernel.Outcome
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import org.log4s.getLogger

object Main extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    val logger = new LoggerImpl[IO](getLogger)

    val appResource: Resource[IO, Unit] =
      for {
        config <- Config.config[IO].resource
        messaging <- MessagingRabbitMq.make(config.messaging, logger)
        routes = {
          val publisher = new PublisherImpl[IO](messaging, logger)
          new RoutesImpl[IO](publisher, logger)
        }
        subscriber = new SubscriberImpl[IO](messaging)
        _ <- Server.resource[IO](routes, config.server, logger)
        _ <- Resource.eval(
          new ReportServiceImpl[IO](config.report, logger).report(subscriber.subscribe()).compile.drain.start
        )
      } yield ()

    appResource.useForever
      .handleErrorWith { e =>
        logger.error(s"Fatal error: ${e.getMessage}") >> IO.pure(ExitCode.Error)
      }
      .guaranteeCase {
        case Outcome.Succeeded(_) => logger.info("Application exited normally")
        case Outcome.Canceled() => logger.info("Application canceled gracefully") *> IO(System.out.flush())
        case Outcome.Errored(e) => logger.error(s"Application failed: ${e.getMessage}")
      }
      .as(ExitCode.Success)
  }
}
