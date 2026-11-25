package bzh.ya2o.telemetry
package application

import cats.effect.testkit.TestControl
import cats.effect.IO
import fs2.Stream
import munit.CatsEffectSuite
import scala.concurrent.duration._

class BackgroundStreamSuite extends CatsEffectSuite {

  private case class Boom() extends RuntimeException("boom")

  test("runForever_streamFails_raisesTheStreamsError") {
    // Arrange
    val failing = Stream.emit(1).covary[IO] ++ Stream.raiseError[IO](Boom())

    // Act
    val result = BackgroundStream.runForever("test", failing).attempt

    // Assert
    result.map(outcome => assertEquals(outcome, Left(Boom())))
  }

  test("runForever_streamEnds_raisesIllegalStateNamingTheStream") {
    // Arrange
    val finite = Stream.emits(List(1, 2, 3)).covary[IO]

    // Act
    val result = BackgroundStream.runForever("reports", finite).attempt

    // Assert
    result.map {
      case Left(error: IllegalStateException) => assertEquals(error.getMessage, "Stream [reports] ended")
      case other => fail(s"expected IllegalStateException, got [$other]")
    }
  }

  test("runForever_streamKeepsRunning_neverCompletes") {
    // Arrange
    val endless = Stream.awakeEvery[IO](1.second)

    // Act
    val result = TestControl.executeEmbed(BackgroundStream.runForever("ticks", endless).timeout(1.hour).attempt)

    // Assert
    result.map(outcome => assert(outcome.left.exists(_.isInstanceOf[java.util.concurrent.TimeoutException])))
  }

}
