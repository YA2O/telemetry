package bzh.ya2o.telemetry
package server

import bzh.ya2o.telemetry.model.Measurement
import bzh.ya2o.telemetry.server.JsonDecoders._
import cats.effect.Async
import cats.implicits._
import org.http4s.circe.CirceEntityDecoder
import org.http4s.dsl.Http4sDsl
import org.http4s.EntityDecoder
import org.http4s.HttpRoutes

object Routes {

  def publicRoutes[F[_]: Async](publisher: Publisher[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] { case req @ POST -> Root / "telemetry" / "cpu" =>
      implicit val entityDecoder: EntityDecoder[F, Measurement] = CirceEntityDecoder.circeEntityDecoder
      req.decode[Measurement] { data =>
        (for {
          _ <- publisher.publish(data)
          resp <- Accepted()
        } yield resp)
      }
    }
  }

}
