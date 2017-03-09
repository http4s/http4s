package org.http4s.client.impl

import java.util.concurrent.ExecutorService
import org.http4s.util.threads._

private[client] object DefaultExecutor {
  /** create a new default executor */
  @deprecated("Use org.http4s.util.threads.newDaemonPool instead", "0.15.7")
  def newClientDefaultExecutorService(name: String): ExecutorService =
    newDefaultFixedThreadPool(
      (Runtime.getRuntime.availableProcessors * 1.5).ceil.toInt,
      threadFactory(i => s"http4s-$name-$i"))
}
