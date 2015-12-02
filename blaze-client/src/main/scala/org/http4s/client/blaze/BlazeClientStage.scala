package org.http4s.client.blaze

import java.nio.ByteBuffer

import scala.util.control.NonFatal

import org.http4s.blaze.pipeline.TailStage
import org.http4s.{Request, Response}

import scalaz.concurrent.Task

trait BlazeClientStage extends TailStage[ByteBuffer] {
  def runRequest(req: Request): Task[Response]

  def isClosed(): Boolean

  def shutdown(): Unit

  override protected def finalize(): Unit = {
    try if (!isClosed()) {
      logger.warn("BlazeClientStage was not disconnected and could result in a resource leak")
      shutdown()
      super.finalize()
    } catch {
      case NonFatal(t) =>
        logger.error(t)("Failure finalizing the client stage")
    }
  }
}
