package bzh.ya2o

import cats.data.Validated

package object telemetry {
  type Validated_[A] = Validated[String, A]
}
