package bzh.ya2o.telemetry
package logic

import io.circe.Encoder
import java.time.Instant
import scala.concurrent.duration._

/** What happened since the last tick, accumulated message by message. */
final case class TickStats(count: Long, delaySum: FiniteDuration, delayMax: FiniteDuration) {

  /** Adds 1 processed message that waited `delay` between being sent and being counted. */
  def add(delay: FiniteDuration): TickStats =
    TickStats(count + 1, delaySum + delay, delayMax.max(delay))

}

object TickStats {
  val Empty: TickStats = TickStats(0, Duration.Zero, Duration.Zero)

  /** Time between `sentAt` and `countedAt`; 0 if the sender's clock is ahead of ours. */
  def delayBetween(sentAt: Instant, countedAt: FiniteDuration): FiniteDuration =
    (countedAt - sentAt.toEpochMilli.millis).max(Duration.Zero)
}

/** Messages waiting in RabbitMQ (`ready`) and delivered but not yet acknowledged (`unacked`). */
final case class QueueDepth(ready: Long, unacked: Long)

/** 1 second of pipeline activity, as published to the dashboard. */
final case class PipelineStats(
  processedPerSec: Long,
  delayAvgMillis: Long,
  delayMaxMillis: Long,
  queue: Option[QueueDepth],
  knobDelayMillis: Long
)

object PipelineStats {

  /** Builds the stats of a 1-second tick. The average delay is 0 when nothing was processed. */
  def of(tick: TickStats, queue: Option[QueueDepth], knob: ProcessingDelay): PipelineStats =
    PipelineStats(
      processedPerSec = tick.count,
      delayAvgMillis = if (tick.count == 0) 0L else tick.delaySum.toMillis / tick.count,
      delayMaxMillis = tick.delayMax.toMillis,
      queue = queue,
      knobDelayMillis = knob.millis
    )

  implicit val queueDepthEncoder: Encoder[QueueDepth] =
    Encoder.forProduct2("ready", "unacked")(q => (q.ready, q.unacked))

  implicit val encoder: Encoder[PipelineStats] =
    Encoder.forProduct5("processedPerSec", "delayAvgMillis", "delayMaxMillis", "queue", "knobDelayMillis") {
      s: PipelineStats => (s.processedPerSec, s.delayAvgMillis, s.delayMaxMillis, s.queue, s.knobDelayMillis)
    }

}
