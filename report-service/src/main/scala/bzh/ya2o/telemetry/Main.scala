package bzh.ya2o.telemetry

import bzh.ya2o.telemetry.application.config.Config
import bzh.ya2o.telemetry.application.logging.LoggerImpl
import bzh.ya2o.telemetry.application.BackgroundStream
import bzh.ya2o.telemetry.application.Server
import bzh.ya2o.telemetry.integration.http.RoutesImpl
import bzh.ya2o.telemetry.integration.json.JsonDecoders._
import bzh.ya2o.telemetry.integration.json.JsonEncoders._
import bzh.ya2o.telemetry.integration.messaging.rabbitmq.MessagingRabbitMq
import bzh.ya2o.telemetry.integration.messaging.SubscriberImpl
import bzh.ya2o.telemetry.logic.ProcessingDelay
import bzh.ya2o.telemetry.logic.ReportService.Report
import bzh.ya2o.telemetry.logic.ReportServiceImpl
import cats.effect.kernel.Outcome
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Ref
import cats.effect.Resource
import fs2.Stream
import org.log4s.getLogger

object Main extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    val logger = new LoggerImpl[IO](getLogger)

    // The HTTP server lives as long as the report stream runs. If the stream fails or ends, the
    // app stops with the error logged and a non-zero exit code, and Docker restarts it.
    val appResource: Resource[IO, Stream[IO, Report]] =
      for {
        config <- Config.config[IO].resource
        messaging <- MessagingRabbitMq.make(config.messaging, logger)
        processingDelay <- Resource.eval(Ref[IO].of(ProcessingDelay.None))
        routes = new RoutesImpl[IO](processingDelay)
        subscriber = new SubscriberImpl[IO](messaging)
        _ <- Server.resource[IO](routes, config.server, logger)
      } yield new ReportServiceImpl[IO](config.report, processingDelay, logger).report(subscriber.subscribe())

    val program: IO[ExitCode] = appResource.use(reports => BackgroundStream.runForever("reports", reports))

    program
      .guaranteeCase {
        case Outcome.Succeeded(_) => logger.info("Application exited normally")
        case Outcome.Canceled() => logger.info("Application canceled gracefully") *> IO(System.out.flush())
        case Outcome.Errored(e) => logger.error(s"Application failed: $e")
      }
      .handleError(_ => ExitCode.Error)
  }
}
