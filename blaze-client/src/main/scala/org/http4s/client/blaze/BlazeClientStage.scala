package org.http4s.client.blaze

import java.nio.ByteBuffer

import scala.util.control.NonFatal

import org.http4s.blaze.pipeline.TailStage
import org.http4s.{Request, Response}

import scalaz.concurrent.Task

trait BlazeClientStage extends TailStage[ByteBuffer] {
  def runRequest(req: Request, preludeFlush: Boolean): Task[Response]

  /** Determine if the stage is closed and resources have been freed */
  def isClosed: Boolean

  /** Determine if the connection can be recycled */
  def isRecyclable: Boolean

  /** Close down the stage
   *  Freeing resources and potentially aborting a [[Response]]
   */
  def shutdown(): Unit

  override protected def finalize(): Unit = {
    try if (!isClosed) {
      logger.warn("BlazeClientStage was not disconnected and could result in a resource leak")
      shutdown()
      super.finalize()
    } catch {
      case NonFatal(t) =>
        logger.error(t)("Failure finalizing the client stage")
    }
  }

  /** The key for requests we are able to serve */
  def requestKey: RequestKey
}
