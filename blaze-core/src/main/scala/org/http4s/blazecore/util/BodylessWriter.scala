package org.http4s
package blazecore
package util

import cats.effect._
import cats.implicits._
import fs2._
import java.nio.ByteBuffer
import org.http4s.blaze.pipeline._
import org.http4s.util.StringWriter
import scala.concurrent._

/** Discards the body, killing it so as to clean up resources
  *
  * @param headers ByteBuffer representation of [[Headers]] to send
  * @param pipe the blaze `TailStage`, which takes ByteBuffers which will send the data downstream
  * @param ec an ExecutionContext which will be used to complete operations
  */
private[http4s] class BodylessWriter[F[_]](pipe: TailStage[ByteBuffer], close: Boolean)(
    implicit protected val F: Effect[F],
    protected val ec: ExecutionContext)
    extends Http1Writer[F] {

  def writeHeaders(headerWriter: StringWriter): Future[Unit] =
    pipe.channelWrite(Http1Writer.headersToByteBuffer(headerWriter.result))

  /** Doesn't write the entity body, just the headers. Kills the stream, if an error if necessary
    *
    * @param p an entity body that will be killed
    * @return the F which, when run, will send the headers and kill the entity body
    */
  override def writeEntityBody(p: EntityBody[F]): F[Boolean] =
    p.drain.compile.drain.map(_ => close)

  override protected def writeEnd(chunk: Chunk[Byte]): Future[Boolean] =
    Future.successful(close)

  override protected def writeBodyChunk(chunk: Chunk[Byte], flush: Boolean): Future[Unit] =
    FutureUnit
}
