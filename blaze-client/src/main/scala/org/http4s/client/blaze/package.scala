package org.http4s.client

import org.http4s.blaze.util.TickWheelExecutor

import scala.concurrent.duration._

package object blaze {

  // Centralize some defaults
  private[blaze] val defaultTimeout: Duration = 60.seconds
  private[blaze] val defaultBufferSize: Int = 8*1024

  private[blaze] val tickWheel = new TickWheelExecutor()
}
