package org.http4s.zipkin

import scalaz.concurrent.Task
import scalaz.{Free, ~>}

package object algebras {
  type Collector[A] = Free.FreeC[CollectorOp, A]
  type CollectorInterpreter = (CollectorOp ~> Task)

}
