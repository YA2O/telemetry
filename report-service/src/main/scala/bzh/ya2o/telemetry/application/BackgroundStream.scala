package bzh.ya2o.telemetry
package application

import cats.effect.Sync
import cats.implicits._
import fs2.Stream

object BackgroundStream {

  /** Runs a stream that is meant to run for the whole life of the app.
    *
    * It never completes normally: if the stream fails, its error is raised; if it ends, that is
    * itself an error. Either way the caller fails, so a dead stream cannot go unnoticed.
    */
  def runForever[F[_], A](name: String, stream: Stream[F, A])(implicit F: Sync[F]): F[Nothing] =
    stream.compile.drain.flatMap[Nothing] { _ =>
      F.raiseError[Nothing](new IllegalStateException(s"Stream [$name] ended"))
    }

}
