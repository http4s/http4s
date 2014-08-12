package org.http4s.blaze.util

import scodec.bits.ByteVector

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz.stream.Process._
import scalaz.stream.Cause.{Terminated, Error}
import scalaz.{-\/, \/, \/-}

trait ProcessWriter {

  implicit protected def ec: ExecutionContext

  type CBType = Throwable \/ Unit => Unit

  /** write a BodyChunk to the wire
    * If a request is cancelled, or the stream is closed this method should
    * return a failed Future with Cancelled as the exception
    *
    * @param chunk BodyChunk to write to wire
    * @return a future letting you know when its safe to continue
    */
  protected def writeBodyChunk(chunk: ByteVector, flush: Boolean): Future[Unit]

  /** Write the ending chunk and, in chunked encoding, a trailer to the wire.
    * If a request is cancelled, or the stream is closed this method should
    * return a failed Future with Cancelled as the exception
    *
    * @param chunk BodyChunk to write to wire
    * @return a future letting you know when its safe to continue
    */
  protected def writeEnd(chunk: ByteVector): Future[Unit]

  def requireClose(): Boolean = false

  /** Called in the event of an Await failure to alert the pipeline to cleanup */
  protected def exceptionFlush(): Future[Unit] = Future.successful(())

  /** Creates a Task that writes the contents the Process to the output.
    * Cancelled exceptions fall through to the Task cb
    * This method will halt writing the process once a trailer is encountered
    *
    * @param p Process[Task, Chunk] to write out
    * @return the Task which when run will unwind the Process
    */
  def writeProcess(p: Process[Task, ByteVector]): Task[Unit] = {
    val channel = scalaz.stream.io.channel { chunk: ByteVector => Task.async { cb: CBType =>
      writeBodyChunk(chunk, false).onComplete(completionListener(_, cb))
    }}
    val finish = eval(Task.async { cb: CBType =>
      writeEnd(ByteVector.empty).onComplete(completionListener(_, cb))
    })
    (p.through(channel) ++ finish).run
  }

  private def completionListener(t: Try[_], cb: CBType): Unit = t match {
    case Success(_) =>  cb(\/-(()))
    case Failure(t) =>  cb(-\/(t))
  }
}