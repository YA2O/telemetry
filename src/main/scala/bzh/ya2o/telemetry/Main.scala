package bzh.ya2o.telemetry

import bzh.ya2o.telemetry.application.config.Config
import bzh.ya2o.telemetry.application.logging.LoggerImpl
import bzh.ya2o.telemetry.application.Server
import bzh.ya2o.telemetry.integration.broker.PublisherImpl
import bzh.ya2o.telemetry.integration.broker.StreamingMiddlewareImpl
import bzh.ya2o.telemetry.integration.http.RoutesImpl
import bzh.ya2o.telemetry.logic.ReportServiceImpl
import bzh.ya2o.telemetry.model.CpuMeasurement
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import fs2.Stream
import org.log4s.getLogger

object Main extends IOApp {

  private val logger = new LoggerImpl[IO](getLogger)

  def run(args: List[String]): IO[ExitCode] = {
    (for {
      config <- Stream.eval(Config.config[IO].load)
      streamingMiddleware <- Stream.eval(StreamingMiddlewareImpl.make[IO, CpuMeasurement](config.middleware))
      _ <- {
        val publisher = new PublisherImpl[IO](streamingMiddleware, logger)
        val routes = new RoutesImpl[IO](publisher, logger)
        val reportService = new ReportServiceImpl[IO](config.report, logger)
        Stream(
          Stream.resource(Server.resource[IO](routes, config.server)),
          reportService.report(streamingMiddleware.subscribe())
        ).parJoinUnbounded
      }
    } yield ()).compile.drain
      .onError { throwable =>
        logger.error(throwable.getMessage)
      }
      .as(ExitCode.Success)
  }

}
