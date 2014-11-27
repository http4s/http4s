package org.http4s.client

import scala.concurrent.duration._

package object blaze {

  // Centralize some defaults
  private[blaze] val defaultTimeout: Duration = 60.seconds
  private[blaze] val defaultBufferSize: Int = 8*1024

}
