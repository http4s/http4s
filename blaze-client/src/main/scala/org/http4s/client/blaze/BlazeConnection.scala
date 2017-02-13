package org.http4s
package client
package blaze

import java.nio.ByteBuffer

import org.http4s.blaze.pipeline.TailStage

import scala.util.control.NonFatal
import fs2.Task

private trait BlazeConnection extends TailStage[ByteBuffer] with Connection {

  def runRequest(req: Request): Task[Response]

  override protected def finalize(): Unit = {
    try if (!isClosed) {
      logger.warn("Client was not shut down and could result in a resource leak")
      shutdown()
      super.finalize()
    } catch {
      case NonFatal(t) =>
        logger.error(t)("Failure finalizing the client")
    }
  }
}
