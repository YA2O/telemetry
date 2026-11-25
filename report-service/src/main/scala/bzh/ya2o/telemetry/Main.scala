package bzh.ya2o.telemetry

import bzh.ya2o.telemetry.application.config.Config
import bzh.ya2o.telemetry.application.logging.LoggerImpl
import bzh.ya2o.telemetry.application.BackgroundStream
import bzh.ya2o.telemetry.application.Server
import bzh.ya2o.telemetry.integration.http.RabbitMqManagement
import bzh.ya2o.telemetry.integration.http.RoutesImpl
import bzh.ya2o.telemetry.integration.json.JsonDecoders._
import bzh.ya2o.telemetry.integration.json.JsonEncoders._
import bzh.ya2o.telemetry.integration.messaging.rabbitmq.MessagingRabbitMq
import bzh.ya2o.telemetry.integration.messaging.SubscriberImpl
import bzh.ya2o.telemetry.logic.PipelineStats
import bzh.ya2o.telemetry.logic.PipelineStatsService
import bzh.ya2o.telemetry.logic.ProcessingDelay
import bzh.ya2o.telemetry.logic.ReportService.Report
import bzh.ya2o.telemetry.logic.ReportServiceImpl
import bzh.ya2o.telemetry.logic.TickStats
import cats.effect.kernel.Outcome
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Ref
import cats.effect.Resource
import fs2.concurrent.SignallingRef
import fs2.Stream
import org.http4s.ember.client.EmberClientBuilder
import org.log4s.getLogger

object Main extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    val logger = new LoggerImpl[IO](getLogger)

    // The HTTP server lives as long as the report and stats streams run. If either fails or ends,
    // the app stops with the error logged and a non-zero exit code, and Docker restarts it.
    val appResource: Resource[IO, Stream[IO, Unit]] =
      for {
        config <- Config.config[IO].resource
        messaging <- MessagingRabbitMq.make(config.messaging, logger)
        httpClient <- EmberClientBuilder.default[IO].build
        processingDelay <- Resource.eval(Ref[IO].of(ProcessingDelay.None))
        tickStats <- Resource.eval(Ref[IO].of(TickStats.Empty))
        latestStats <- Resource.eval(SignallingRef[IO].of(Option.empty[PipelineStats]))
        latestReport <- Resource.eval(SignallingRef[IO].of(Option.empty[Report]))
        routes = new RoutesImpl[IO](processingDelay, latestStats, latestReport)
        subscriber = new SubscriberImpl[IO](messaging)
        reportService = new ReportServiceImpl[IO](config.report, processingDelay, tickStats, logger)
        statsService = new PipelineStatsService[IO](
          tickStats,
          processingDelay,
          new RabbitMqManagement[IO](httpClient, config.messaging),
          latestStats,
          logger
        )
        _ <- Server.resource[IO](routes, config.server, logger)
        reports = reportService.report(subscriber.subscribe()).evalTap(report => latestReport.set(Some(report)))
      } yield reports.drain.mergeHaltBoth(statsService.run.drain)

    val program: IO[ExitCode] =
      appResource.use(streams => BackgroundStream.runForever("reports and stats", streams))

    program
      .guaranteeCase {
        case Outcome.Succeeded(_) => logger.info("Application exited normally")
        case Outcome.Canceled() => logger.info("Application canceled gracefully") *> IO(System.out.flush())
        case Outcome.Errored(e) => logger.error(s"Application failed: $e")
      }
      .handleError(_ => ExitCode.Error)
  }
}
