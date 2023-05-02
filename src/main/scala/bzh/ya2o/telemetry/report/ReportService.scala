package bzh.ya2o.telemetry
package report

import bzh.ya2o.telemetry.config.ReportConfig
import bzh.ya2o.telemetry.logging.Logger
import bzh.ya2o.telemetry.model.Measurement
import bzh.ya2o.telemetry.model.Measurement.ClientVersion
import bzh.ya2o.telemetry.report.ReportService.Report
import bzh.ya2o.telemetry.report.ReportService.VersionCounter
import cats.effect._
import cats.implicits._
import fs2.Stream
import io.circe.syntax._
import io.circe.Encoder
import java.time.Instant
import scala.collection.SortedMap
import scala.concurrent.duration._
trait ReportService[F[_]] {
  def report(input: Stream[F, Measurement]): Stream[F, Report]
}

object ReportService {

  final case class Report(
    timestamp: Instant,
    period: FiniteDuration,
    counters: List[VersionCounter]
  )
  final case class VersionCounter(
    clientVersion: ClientVersion,
    count: Long,
    countPerDivision: SortedMap[Int, Long]
  )

  object Report {
    implicit val instantEncoder: Encoder[Instant] = Encoder[String].contramap(_.toString)
    implicit val durationEncoder: Encoder[FiniteDuration] = Encoder[String].contramap(_.toString)
    implicit val clientVersionEncoder: Encoder[ClientVersion] = Encoder[String].contramap(_.value)
    implicit val counterEncoder: Encoder[VersionCounter] =
      Encoder.forProduct3("clientVersion", "count", "countPerDivision") { counter =>
        (counter.clientVersion, counter.count, counter.countPerDivision)
      }
    implicit val encoder: Encoder[Report] =
      Encoder.forProduct3("timestamp", "period", "counters") { report: Report =>
        (report.timestamp, report.period, report.counters)
      }
  }

}

class ReportServiceImpl[F[_]](config: ReportConfig)(implicit F: Async[F], logger: Logger[F])
  extends ReportService[F] {

  case class Counter private (
    count: Long,
    countPerDivision: Map[Int, Long]
  )

  override def report(input: Stream[F, Measurement]): Stream[F, Report] = {

    def computeDecimalDivision(f: Float): Int = {
      // Compute in which "division" a value belongs, i.e. 0 < division0 ≤ 10 < division10 ≤ 20 < division20, etc.
      (Math.ceil(f.toDouble / 10).toInt - 1) * 10
    }

    def updateCounters(
      input: Stream[F, Measurement],
      counters: Ref[F, Map[ClientVersion, Counter]]
    ): Stream[F, Measurement] = {
      input.evalTap { measurement =>
        logger.debug(s"<<< (consuming) $measurement") >> counters.update {
          (counters: Map[ClientVersion, Counter]) =>
            val clientVersion = measurement.clientVersion
            val division = computeDecimalDivision(measurement.cpu.value)
            counters.updatedWith(clientVersion) {
              case Some(counter) =>
                Some(
                  counter.copy(
                    count = counter.count + 1,
                    counter.countPerDivision.updatedWith(division) {
                      case Some(count) => Some(count + 1)
                      case None => Some(1)
                    }
                  )
                )
              case None => Some(Counter(count = 1, countPerDivision = Map(division -> 1)))
            }
        }
      }
    }

    def reportAndResetCounters(counters: Ref[F, Map[ClientVersion, Counter]]): Stream[F, Report] = {
      Stream
        .awakeEvery[F](config.period)
        .evalMap(_ => counters.getAndSet(Map.empty[ClientVersion, Counter]))
        .evalMap { counters: Map[ClientVersion, Counter] =>
          now().map { instant =>
            val versionCounters = counters.map { case (clientVersion, counter) =>
              VersionCounter(
                clientVersion,
                counter.count,
                SortedMap.empty[Int, Long] ++ counter.countPerDivision
              )
            }.toList

            Report(
              instant,
              config.period,
              versionCounters
            )
          }
        }
    }

    for {
      counters <- Stream.eval(Ref[F].of(Map.empty[ClientVersion, Counter]))
      report <- reportAndResetCounters(counters) concurrently updateCounters(input, counters)
      _ <- Stream.eval(logger.info(s"${report.asJson.spaces2}"))
    } yield report

  }

  private[this] def now(): F[Instant] = F.realTime.map { (time: FiniteDuration) =>
    Instant.ofEpochMilli(time.toMillis)
  }

}
