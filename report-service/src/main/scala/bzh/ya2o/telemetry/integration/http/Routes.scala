package bzh.ya2o.telemetry
package integration.http

import bzh.ya2o.telemetry.application.error.CustomError
import bzh.ya2o.telemetry.application.error.CustomError.YourClientBadRequestError
import bzh.ya2o.telemetry.logic.ProcessingDelay
import cats.effect.Async
import cats.effect.Ref
import cats.implicits._
import io.circe.syntax._
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import org.http4s.circe.jsonDecoder
import org.http4s.circe.jsonEncoder
import org.http4s.circe.jsonOf
import org.http4s.dsl.Http4sDsl
import org.http4s.HttpRoutes
import org.http4s.Request
import org.http4s.Response

trait Routes[F[_]] {
  def publicRoutes: HttpRoutes[F]
}

class RoutesImpl[F[_]](processingDelay: Ref[F, ProcessingDelay])(implicit F: Async[F]) extends Routes[F] {

  private[this] val dsl = new Http4sDsl[F] {}
  import dsl._

  override def publicRoutes: HttpRoutes[F] = {
    val jsonRoutes: PartialFunction[Request[F], Json => F[Response[F]]] = {
      case _ @GET -> Root / "telemetry" / "cpu" => (_ => NotImplemented())
      //case _ @POST -> Root / "telemetry" / "cpu" => createCpuMeasurement
    }

    HttpRoutes.of[F] { case req @ jsonRoutes(jsonEndpoint) =>
      req.decodeStrict[Json](jsonEndpoint) |> handleErrors
    } <+> knobRoutes
  }

  /** The slow-down knob: `GET` reads the delay per message, `PUT {"millis": 50}` sets it. */
  private[this] def knobRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "knob" / "delay" =>
      processingDelay.get.flatMap(delay => Ok(DelayBody(delay.millis).asJson))

    case req @ PUT -> Root / "knob" / "delay" =>
      req.attemptAs[DelayBody](jsonOf[F, DelayBody]).value.flatMap {
        case Left(failure) => BadRequest(s"Expected a body like {\"millis\": 50}: ${failure.message}")
        case Right(body) =>
          ProcessingDelay.fromMillis(body.millis) match {
            case Left(error) => BadRequest(error)
            case Right(delay) => processingDelay.set(delay) >> Ok(DelayBody(delay.millis).asJson)
          }
      }
  }

  private[this] def handleErrors(f: F[Response[F]]): F[Response[F]] = {
    def errorToResponse(error: Throwable): F[Response[F]] = error match {
      case err: CustomError => {
        err match {
          case YourClientBadRequestError(message) => BadRequest.apply(message)
          case CustomError.OurInternalError(_) => ???
          case CustomError.TheirServerError(_) => ???
        }
      }
      case err =>
        InternalServerError.apply(s"Unhandled error: [${err.getMessage}]!")
    }

    f.recoverWith { case err => errorToResponse(err) }
  }

}

/** JSON body of the knob endpoints: `{"millis": 50}`. */
final case class DelayBody(millis: Long)

object DelayBody {
  implicit val decoder: Decoder[DelayBody] = Decoder.forProduct1("millis")(DelayBody.apply)
  implicit val encoder: Encoder[DelayBody] = Encoder.forProduct1("millis")(_.millis)
}
