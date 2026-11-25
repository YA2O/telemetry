package bzh.ya2o.telemetry
package logic

import munit.FunSuite

class ProcessingDelaySuite extends FunSuite {

  test("fromMillis_boundaries_acceptsOnly0To60000") {
    // Arrange
    val cases = List(
      -1L -> false,
      0L -> true,
      50L -> true,
      60000L -> true,
      60001L -> false,
      Long.MaxValue -> false
    )

    cases.foreach { case (millis, expectedValid) =>
      // Act
      val result = ProcessingDelay.fromMillis(millis)

      // Assert
      assertEquals(result.isRight, expectedValid, s"millis = [$millis]")
      result.foreach(delay => assertEquals(delay.millis, millis, s"millis = [$millis]"))
    }
  }

}
