package org.http4s
package blaze
package util

import java.nio.ByteBuffer

import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import fs2.Stream._
import fs2._
import org.http4s.blaze.pipeline._

import scala.concurrent._
import scala.util._

/** Discards the body, killing it so as to clean up resources
  *
  * @param headers ByteBuffer representation of [[Headers]] to send
  * @param pipe the blaze `TailStage`, which takes ByteBuffers which will send the data downstream
  * @param ec an ExecutionContext which will be used to complete operations
  */
class BodylessWriter[F[_]](headers: ByteBuffer,
                           pipe: TailStage[ByteBuffer],
                           close: Boolean)
                          (implicit protected val F: Effect[F],
                           protected val ec: ExecutionContext) extends EntityBodyWriter[F] {

  private lazy val doneFuture = Future.successful(())

  /** Doesn't write the entity body, just the headers. Kills the stream, if an error if necessary
    *
    * @param p an entity body that will be killed
    * @return the F which, when run, will send the headers and kill the entity body
    */

  override def writeEntityBody(p: EntityBody[F]): F[Boolean] =
    F.async { cb =>
      val callback = cb
        .compose((t: Either[Throwable, Unit]) => t.map(_ => close))
        .andThen(IO(_))

      pipe.channelWrite(headers).onComplete {
        case Success(_) =>
          async.unsafeRunAsync(p.run)(callback)
        case Failure(t) =>
          async.unsafeRunAsync(Stream.fail(t).covary[F].run)(callback)
      }
    }

  override protected def writeEnd(chunk: Chunk[Byte]): Future[Boolean] =
    doneFuture.map(_ => close)

  override protected def writeBodyChunk(chunk: Chunk[Byte], flush: Boolean): Future[Unit] =
    doneFuture
}
