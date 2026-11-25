package bzh.ya2o.telemetry
package logic

import scala.concurrent.duration._

/** Extra time spent on each message before it is counted: the slow-down knob for demos.
  * Between 0 and [[ProcessingDelay.MaxMillis]] ms, so it can neither be negative nor overflow.
  */
sealed abstract case class ProcessingDelay private (value: FiniteDuration) {
  def millis: Long = value.toMillis
}

object ProcessingDelay {
  val MaxMillis: Long = 60000L

  val None: ProcessingDelay = new ProcessingDelay(Duration.Zero) {}

  def fromMillis(millis: Long): Either[String, ProcessingDelay] =
    if (millis >= 0 && millis <= MaxMillis) Right(new ProcessingDelay(millis.millis) {})
    else Left(s"Delay must be between 0 and $MaxMillis ms, got [$millis]")
}
