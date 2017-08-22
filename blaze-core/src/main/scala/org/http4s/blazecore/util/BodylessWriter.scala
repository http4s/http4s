package org.http4s
package blazecore
package util

import java.nio.ByteBuffer

import scala.concurrent._
import scala.util._

import cats.implicits._
import fs2._
import fs2.Stream._
import fs2.interop.cats._
import fs2.util.Attempt
import org.http4s.blaze.pipeline._

/** Discards the body, killing it so as to clean up resources
  *
  * @param headers ByteBuffer representation of [[Headers]] to send
  * @param pipe the blaze `TailStage`, which takes ByteBuffers which will send the data downstream
  * @param ec an ExecutionContext which will be used to complete operations
  */
class BodylessWriter(headers: ByteBuffer, pipe: TailStage[ByteBuffer], close: Boolean)
                    (implicit protected val ec: ExecutionContext) extends EntityBodyWriter {

  private lazy val doneFuture = Future.successful( () )

  /** Doesn't write the entity body, just the headers. Kills the stream, if an error if necessary
    *
    * @param p an entity body that will be killed
    * @return the Task which, when run, will send the headers and kill the entity body
    */
  override def writeEntityBody(p: EntityBody): Task[Boolean] = Task.async { cb =>
    val callback = cb.compose((t: Attempt[Unit]) => t.map(_ => close))

    pipe.channelWrite(headers).onComplete {
      case Success(_) => p.open.close.run.unsafeRunAsync(callback)
      case Failure(t) => p.pull(_ => Pull.fail(t)).run.unsafeRunAsync(callback)
    }
  }

  override protected def writeEnd(chunk: Chunk[Byte]): Future[Boolean] = doneFuture.map(_ => close)

  override protected def writeBodyChunk(chunk: Chunk[Byte], flush: Boolean): Future[Unit] = doneFuture
}
