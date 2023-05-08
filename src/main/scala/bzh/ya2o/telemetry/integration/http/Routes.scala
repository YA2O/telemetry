package bzh.ya2o.telemetry
package integration.http

import bzh.ya2o.telemetry.application.error.CustomError
import bzh.ya2o.telemetry.application.error.CustomError.YourClientBadRequestError
import bzh.ya2o.telemetry.application.logging.Logger
import bzh.ya2o.telemetry.integration.broker.Publisher
import bzh.ya2o.telemetry.integration.http.JsonDecoders._
import bzh.ya2o.telemetry.model.CpuMeasurement
import bzh.ya2o.telemetry.Validated_
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

  override def publicRoutes: HttpRoutes[F] = {
    val jsonRoutes: PartialFunction[Request[F], Json => F[Response[F]]] = {
      case _ @POST -> Root / "telemetry" / "cpu" => createCpuMeasurement
    }

    HttpRoutes.of[F] { case req @ jsonRoutes(jsonEndpoint) =>
      req.decodeStrict[Json](jsonEndpoint) |> handleErrors
    }
  }

  private[this] val createCpuMeasurement: Json => F[Response[F]] = { json =>
    def parseCpuMeasurement(json: Json): Validated_[CpuMeasurement] = {
      decodeAccumulating[CpuMeasurement](json.toString())
        .leftMap(_.map(_.getMessage))
        .toVnec
        .toValidated
    }

    logger.debug(s"Received JSON request: [$json]") >>
      (for {
        measurement <- (parseCpuMeasurement(json).leftMap(YourClientBadRequestError)) |> F.fromValidated
        _ <- publisher.publish(measurement)
        resp <- Accepted()
      } yield resp)
  }

  private[this] def handleErrors(f: F[Response[F]]): F[Response[F]] = {
    def errorToResponse(error: Throwable): F[Response[F]] = error match {
      case err: CustomError => {
        err match {
          case YourClientBadRequestError(message) =>
            BadRequest.apply(message)
        }
      }
      case err =>
        InternalServerError.apply(s"Unhandled error: [${err.getMessage}]!")
    }

    f.recoverWith { case err => errorToResponse(err) }
  }

}
