package org.http4s
package client
package blaze

import java.nio.ByteBuffer

import org.http4s.blaze.pipeline.TailStage

import scala.util.control.NonFatal
import scalaz.concurrent.Task

trait BlazeConnection extends TailStage[ByteBuffer] with Connection {
  final def runRequest(req: Request): Task[Response] =
    runRequest(req, false)

  /** If we flush the prelude, we can detect stale connections before we run the effect
    * of the body.  This gives us a better retry story. */
  def runRequest(req: Request, flushPrelude: Boolean): Task[Response]

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
