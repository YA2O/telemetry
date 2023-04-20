package bzh.ya2o.telemetry
package logging

import cats.effect.Sync

trait Logger[F[_]] {
  def error(msg: String): F[Unit]
  def warn(msg: String): F[Unit]
  def info(msg: String): F[Unit]
  def debug(msg: String): F[Unit]
}

class LoggerImpl[F[_]](logger: org.log4s.Logger)(implicit F: Sync[F]) extends Logger[F] {
  override def error(msg: String): F[Unit] = F.delay(logger.error(msg))

  override def warn(msg: String): F[Unit] = F.delay(logger.warn(msg))

  override def info(msg: String): F[Unit] = F.delay(logger.info(msg))

  override def debug(msg: String): F[Unit] = F.delay(logger.debug(msg))
}
