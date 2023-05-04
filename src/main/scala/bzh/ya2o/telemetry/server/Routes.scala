package bzh.ya2o.telemetry
package server

import bzh.ya2o.telemetry.error.CustomError
import bzh.ya2o.telemetry.error.CustomError.YourClientBadRequestError
import bzh.ya2o.telemetry.logging.Logger
import bzh.ya2o.telemetry.model.Measurement
import bzh.ya2o.telemetry.server.JsonDecoders._
import cats.effect.Async
import cats.implicits._
import io.circe.parser._
import io.circe.Json
import org.http4s.circe.jsonDecoder
import org.http4s.dsl.Http4sDsl
import org.http4s.HttpRoutes
import org.http4s.Response

object Routes {

  def publicRoutes[F[_]](publisher: Publisher[F], logger: Logger[F])(implicit F: Async[F]): HttpRoutes[F] = {

    val dsl = new Http4sDsl[F] {}
    import dsl._

    def postCpuMeasurement(json: Json): F[Response[F]] = {
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

    def handleErrors(resp: F[Response[F]]): F[Response[F]] = {
      resp.recoverWith {
        case err: CustomError => {
          err match {
            case YourClientBadRequestError(message) =>
              BadRequest(message)
          }
        }
        case err => InternalServerError(s"Unhandled error: [${err.getMessage}]!")
      }
    }

    // TODO handle authorization
    HttpRoutes.of[F] {
      case req @ POST -> Root / "telemetry" / "cpu" => {
        req.decodeStrict[Json](postCpuMeasurement) |> handleErrors
      }
    }
  }

}
