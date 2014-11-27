package org.http4s.client

import org.http4s.blaze.util.{Execution, TickWheelExecutor}

import scala.concurrent.duration._

package object blaze {

  // Centralize some defaults
  private[blaze] val defaultTimeout: Duration = 60.seconds
  private[blaze] val defaultBufferSize: Int = 8*1024
  private[blaze] def defaultEC = Execution.trampoline

  private[blaze] val tickWheel = new TickWheelExecutor()
}
