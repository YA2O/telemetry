package bzh.ya2o

import cats.data.NonEmptyChain
import cats.data.Validated
import cats.data.ValidatedNec
import cats.data.ValidatedNel
import cats.implicits.catsSyntaxFoldableOps0

package object telemetry {

  type Validated_[A] = Validated[String, A]

  implicit class ThrushOperator[A](private val a: A) extends AnyVal {
    def |>[B](f: A => B): B = f(a)
  }

  implicit final class ValidatedNecStringOps[A](private val vnec: ValidatedNec[String, A]) {
    def toValidated: Validated[String, A] = vnec.leftMap(_.mkString_("; "))
  }

  implicit final class ValidatedNelOps[E, A](private val vnel: ValidatedNel[E, A]) {
    def toVnec: ValidatedNec[E, A] = vnel.leftMap(NonEmptyChain.fromNonEmptyList)
  }

}
