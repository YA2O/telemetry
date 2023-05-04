package bzh.ya2o.telemetry
package application.error

import scala.util.control.NoStackTrace

sealed abstract class CustomError(val message: String) extends Throwable(message) with NoStackTrace

object CustomError {
  // Blame it on your stupid client
  case class YourClientBadRequestError(override val message: String) extends CustomError(message)

  // Blame it on our awesome service
  //case class OurInternalError(override val message: String) extends CustomError(message)

  // Blame it on their awful external API
  //case class TheirServerError(override val message: String) extends CustomError(message)

  // Blame it on the boogie...
}
