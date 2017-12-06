package org.http4s
package blazecore
package util

import cats.effect._
import cats.implicits._
import fs2._
import org.http4s.syntax.async._
import scala.concurrent._

private[http4s] trait EntityBodyWriter[F[_]] {

  implicit protected def F: Effect[F]

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
  protected def exceptionFlush(): Future[Unit] = FutureUnit

  /** Creates an effect that writes the contents of the EntityBody to the output.
    * Cancelled exceptions fall through to the effect cb
    * The writeBodyEnd triggers if there are no exceptions, and the result will
    * be the result of the writeEnd call.
    *
    * @param p EntityBody to write out
    * @return the Task which when run will unwind the Process
    */
  def writeEntityBody(p: EntityBody[F]): F[Boolean] = {
    val writeBody: F[Unit] = (p to writeSink).run
    val writeBodyEnd: F[Boolean] = F.fromFuture(writeEnd(Chunk.empty))
    writeBody *> writeBodyEnd
  }

  /** Writes each of the body chunks, if the write fails it returns
    * the failed future which throws an error.
    * If it errors the error stream becomes the stream, which performs an
    * exception flush and then the stream fails.
    */
  private def writeSink: Sink[F, Byte] = { s =>
    val writeStream: Stream[F, Unit] =
      s.chunks.evalMap(chunk => F.fromFuture(writeBodyChunk(chunk, flush = false)))
    val errorStream: Throwable => Stream[F, Unit] = e =>
      Stream.eval(F.fromFuture(exceptionFlush())).flatMap(_ => Stream.raiseError(e))
    writeStream.handleErrorWith(errorStream)
  }
}
