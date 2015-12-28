package org.http4s.blaze.util

import java.nio.ByteBuffer

import org.http4s.blaze.pipeline.TailStage
import scodec.bits.ByteVector

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scalaz.concurrent.Task
import scalaz.stream.Process

/** Discards the body, killing it so as to clean up resources
  *
  * @param headers ByteBuffer representation of [[Headers]] to send
  * @param pipe the blaze [[TailStage]] which takes ByteBuffers which will send the data downstream
  * @param ec an ExecutionContext which will be used to complete operations
  */
class BodylessWriter(headers: ByteBuffer, pipe: TailStage[ByteBuffer], close: Boolean)
                    (implicit protected val ec: ExecutionContext) extends ProcessWriter {

  private lazy val doneFuture = Future.successful( () )

  /** Doesn't write the process, just the headers and kills the process, if an error if necessary
    *
    * @param p Process[Task, Chunk] that will be killed
    * @return the Task which when run will send the headers and kill the body process
    */
  override def writeProcess(p: Process[Task, ByteVector]): Task[Boolean] = Task.async { cb =>
    val callback = cb.compose((t: scalaz.\/[Throwable, Unit]) => t.map(_ => close))

    pipe.channelWrite(headers).onComplete {
      case Success(_) => p.kill.run.runAsync(callback)
      case Failure(t) => p.kill.onComplete(Process.fail(t)).run.runAsync(callback)
    }
  }

  override protected def writeEnd(chunk: ByteVector): Future[Boolean] = doneFuture.map(_ => close)

  override protected def writeBodyChunk(chunk: ByteVector, flush: Boolean): Future[Unit] = doneFuture
}
