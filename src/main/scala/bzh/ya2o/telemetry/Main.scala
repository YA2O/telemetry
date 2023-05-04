package bzh.ya2o.telemetry

import bzh.ya2o.telemetry.config.Config
import bzh.ya2o.telemetry.logging.LoggerImpl
import bzh.ya2o.telemetry.model.Measurement
import bzh.ya2o.telemetry.report.ReportServiceImpl
import bzh.ya2o.telemetry.server.PublisherImpl
import bzh.ya2o.telemetry.server.RoutesImpl
import bzh.ya2o.telemetry.server.Server
import bzh.ya2o.telemetry.streamingmiddleware.StreamingMiddlewareImpl
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
      streamingMiddleware <- Stream.eval(StreamingMiddlewareImpl.make[IO, Measurement](config.middleware))
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
