package bzh.ya2o.telemetry
package logic

import bzh.ya2o.telemetry.application.logging.Logger
import bzh.ya2o.telemetry.integration.http.QueueDepthSource
import cats.effect.Ref
import cats.effect.Temporal
import cats.implicits._
import fs2.concurrent.SignallingRef
import fs2.Stream
import scala.concurrent.duration._

/** Every second, turns what the report service accumulated in `tickStats` into [[PipelineStats]],
  * adds the queue depth from RabbitMQ, and publishes the result to `latest`.
  *
  * {{{
  *   ReportServiceImpl ── add ──> tickStats ── getAndSet(Empty) every 1 s ──┐
  *   knob ─────────────────────────────────────────────────────── get ──────┤
  *   RabbitMQ management API ── queueDepth (None on error or timeout) ──────┴─> latest
  * }}}
  */
class PipelineStatsService[F[_]](
  tickStats: Ref[F, TickStats],
  knob: Ref[F, ProcessingDelay],
  queueDepthSource: QueueDepthSource[F],
  latest: SignallingRef[F, Option[PipelineStats]],
  logger: Logger[F]
)(
  implicit F: Temporal[F]
) {

  def run: Stream[F, PipelineStats] =
    Stream.awakeEvery[F](1.second).evalMap(_ => tick).evalTap(stats => latest.set(Some(stats)))

  private[logic] def tick: F[PipelineStats] =
    for {
      lastSecond <- tickStats.getAndSet(TickStats.Empty)
      delay <- knob.get
      queue <- queueDepth
    } yield PipelineStats.of(lastSecond, queue, delay)

  private[this] def queueDepth: F[Option[QueueDepth]] =
    F.timeout(queueDepthSource.queueDepth, PipelineStatsService.QueueDepthTimeout)
      .map(Option(_))
      .handleErrorWith(error => logger.warn(s"Queue depth unavailable: [$error]").as(Option.empty[QueueDepth]))

}

object PipelineStatsService {
  val QueueDepthTimeout: FiniteDuration = 800.millis
}
