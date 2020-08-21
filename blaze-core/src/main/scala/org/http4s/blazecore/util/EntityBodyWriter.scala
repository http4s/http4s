/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package blazecore
package util

import cats.effect._
import cats.implicits._
import fs2._
import scala.concurrent._

private[http4s] trait EntityBodyWriter[F[_]] {
  implicit protected def F: Effect[F]

  protected val wroteHeader: Promise[Unit] = Promise[Unit]()

  /** The `ExecutionContext` on which to run computations, assumed to be stack safe. */
  implicit protected def ec: ExecutionContext

  /** Write a Chunk to the wire.
    *
    * @param chunk BodyChunk to write to wire
    * @return a future letting you know when its safe to continue
    */
  protected def writeBodyChunk(chunk: Chunk[Byte], flush: Boolean): Future[Unit]

  /** Write the ending chunk and, in chunked encoding, a trailer to the
    * wire.
    *
    * @param chunk BodyChunk to write to wire
    * @return a future letting you know when its safe to continue (if `false`) or
    * to close the connection (if `true`)
    */
  protected def writeEnd(chunk: Chunk[Byte]): Future[Boolean]

  /** Called in the event of an Await failure to alert the pipeline to cleanup */
  protected def exceptionFlush(): Future[Unit] = FutureUnit

  /** Creates an effect that writes the contents of the EntityBody to the output.
    * The writeBodyEnd triggers if there are no exceptions, and the result will
    * be the result of the writeEnd call.
    *
    * @param p EntityBody to write out
    * @return the Task which when run will unwind the Process
    */
  def writeEntityBody(p: EntityBody[F]): F[Boolean] = {
    val writeBody: F[Unit] = p.through(writePipe).compile.drain
    val writeBodyEnd: F[Boolean] = fromFutureNoShift(F.delay(writeEnd(Chunk.empty)))
    writeBody *> writeBodyEnd
  }

  /** Writes each of the body chunks, if the write fails it returns
    * the failed future which throws an error.
    * If it errors the error stream becomes the stream, which performs an
    * exception flush and then the stream fails.
    */
  private def writePipe: Pipe[F, Byte, Unit] = { s =>
    val writeStream: Stream[F, Unit] =
      s.chunks.evalMap(chunk => fromFutureNoShift(F.delay(writeBodyChunk(chunk, flush = false))))
    val errorStream: Throwable => Stream[F, Unit] = e =>
      Stream
        .eval(fromFutureNoShift(F.delay(exceptionFlush())))
        .flatMap(_ => Stream.raiseError[F](e))
    writeStream.handleErrorWith(errorStream)
  }
}
