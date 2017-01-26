package org.http4s
package blaze
package util

import scala.annotation.tailrec
import scala.concurrent._
import scala.util._

import fs2._
import fs2.Stream._
import org.http4s.batteries._
import org.http4s.util.task._

trait EntityBodyWriter {

  /** The `ExecutionContext` on which to run computations, assumed to be stack safe. */
  implicit protected def ec: ExecutionContext

  /** Write a ByteVector to the wire.
    * If a request is cancelled, or the stream is closed this method should
    * return a failed Future with Cancelled as the exception
    *
    * @param chunk BodyChunk to write to wire
    * @return a future letting you know when its safe to continue
    */
  protected def writeBodyChunk(chunk: Chunk[Byte], flush: Boolean): Future[Unit]

  /** Write the ending chunk and, in chunked encoding, a trailer to the
    * wire.  If a request is cancelled, or the stream is closed this
    * method should return a failed Future with Cancelled as the
    * exception, or a Future with a Boolean to indicate whether the
    * connection is to be closed or not.
    *
    * @param chunk BodyChunk to write to wire
    * @return a future letting you know when its safe to continue (if `false`) or
    * to close the connection (if `true`)
    */
  protected def writeEnd(chunk: Chunk[Byte]): Future[Boolean]

  /** Called in the event of an Await failure to alert the pipeline to cleanup */
  protected def exceptionFlush(): Future[Unit] = Future.successful(())

  /** Creates a Task that writes the contents of the EntityBody to the output.
    * Cancelled exceptions fall through to the Task cb
    *
    * @param p EntityBody to write out
    * @return the Task which when run will unwind the Process
    */
  def writeEntityBody(p: EntityBody): Task[Boolean] = {
    // TODO fs2 port suboptimal vs. scalaz-stream version
    // TODO fs2 port onError is "not for resource cleanup".  This still feels wrong.
    val write = (p through sink).onError { e =>
      eval(futureToTask(exceptionFlush)).flatMap(_ => fail(e))
    } ++ eval(futureToTask[Boolean](writeEnd(Chunk.empty)))
    write.runLast.map(_.getOrElse(false))
  }

  private val sink: Pipe[Task, Byte, Boolean] = { s =>
    // TODO fs2 port a Pipe instead of a sink, and a map true, for type inference issues
    // This is silly, but I'm racing toward something that compiles
    s.chunks.evalMap(chunk => futureToTask(writeBodyChunk(chunk, false)).map(_ => true))
  }
}
