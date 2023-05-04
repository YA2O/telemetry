package bzh.ya2o.telemetry
package server

import bzh.ya2o.telemetry.error.CustomError
import bzh.ya2o.telemetry.error.CustomError.YourClientBadRequestError
import bzh.ya2o.telemetry.logging.Logger
import bzh.ya2o.telemetry.model.Measurement
import bzh.ya2o.telemetry.server.JsonDecoders._
import cats.data.Kleisli
import cats.data.OptionT
import cats.effect.Async
import cats.implicits._
import io.circe.parser._
import io.circe.Json
import org.http4s.circe.jsonDecoder
import org.http4s.dsl.Http4sDsl
import org.http4s.HttpRoutes
import org.http4s.Request
import org.http4s.Response

trait Routes[F[_]] {
  def publicRoutes: HttpRoutes[F]
}

class RoutesImpl[F[_]](publisher: Publisher[F], logger: Logger[F])(implicit F: Async[F]) extends Routes[F] {

  private[this] val dsl = new Http4sDsl[F] {}
  import dsl._

  private[this] val routes: Kleisli[OptionT[F, *], Request[F], Response[F]] = {
    HttpRoutes.of[F] {
      // TODO handle authorization; see org.http4s.server.AuthMiddleware.noSpider
      case req @ POST -> Root / "telemetry" / "cpu" => {
        parseBodyAsJson(req, postCpuMeasurement)
      }
    }
  }

  override def publicRoutes: HttpRoutes[F] = {
    // (convoluted?) way to avoid appending a call to 'handleError' after each route
    Kleisli[OptionT[F, *], Request[F], Response[F]] { req =>
      routes
        .run(req)
        .value
        .recoverWith(handleError(_).map(_.some)) |> OptionT.apply
    }
  }

  private[this] def postCpuMeasurement(json: Json): F[Response[F]] = {
    def parseBody(json: Json): Validated_[Measurement] = {
      decodeAccumulating[Measurement](json.toString())
        .leftMap(_.map(_.getMessage))
        .toVnec
        .toValidated
    }

    logger.debug(s"Received JSON request: [$json]") >>
      (for {
        measurement <- (parseBody(json).leftMap(YourClientBadRequestError)) |> F.fromValidated
        _ <- publisher.publish(measurement)
        resp <- Accepted()
      } yield resp)
  }

  private[this] def parseBodyAsJson(req: Request[F], f: Json => F[Response[F]]): F[Response[F]] =
    req.decodeStrict[Json](f)

  private[this] def handleError(error: Throwable): F[Response[F]] = error match {
    case err: CustomError => {
      err match {
        case YourClientBadRequestError(message) =>
          BadRequest(message)
      }
    }
    case err =>
      InternalServerError(s"Unhandled error: [${err.getMessage}]!")
  }

}
