package org.http4s.client

import org.http4s.blaze.util.{Execution, TickWheelExecutor}

import scala.concurrent.duration._

package object blaze {

  // Centralize some defaults
  private[blaze] val DefaultTimeout: Duration = 60.seconds
  private[blaze] val DefaultBufferSize: Int = 8*1024
  private[blaze] val ClientDefaultEC = Execution.trampoline
  private[blaze] val ClientTickWheel = new TickWheelExecutor()
}
