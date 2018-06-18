/*
 * Portions derived from https://github.com/functional-streams-for-scala/fs2/blob/v0.10.5/io/src/main/scala/fs2/io/JavaInputOutputStream.scala
 * Copyright (c) 2013 Paul Chiusano, and respective contributors
 */
package org.http4s

import cats.effect.{Async, Effect, Sync}
import cats.implicits._
import fs2.{Chunk, Sink, Stream}
import java.io.{InputStream, OutputStream}
import scala.concurrent.ExecutionContext

package object internal {
  private[http4s] def blocking[F[_], A](fa: F[A], blockingExecutionContext: ExecutionContext)(
      implicit F: Async[F],
      ec: ExecutionContext): F[A] =
    for {
      _ <- Async.shift[F](blockingExecutionContext)
      att <- fa.attempt
      _ <- Async.shift(ec)
      fa0 <- F.fromEither(att)
    } yield fa0

  /** Like fs2.io.readInputStream, but does a double shift with [[blocking]]. */
  private[http4s] def readInputStream[F[_]](
      fis: F[InputStream],
      chunkSize: Int,
      blockingExecutionContext: ExecutionContext,
      closeAfterUse: Boolean = true)(
      implicit F: Effect[F],
      ec: ExecutionContext): Stream[F, Byte] = {
    def read(is: InputStream, buf: Array[Byte]) =
      blocking(readBytesFromInputStream(is, buf), blockingExecutionContext)
    readInputStreamGeneric(fis, F.delay(new Array[Byte](chunkSize)), read, closeAfterUse)
  }

  /** Like fs2.io.writeOutputStream, but does a double shift with [[blocking]]. */
  private[http4s] def writeOutputStream[F[_]](
      fos: F[OutputStream],
      blockingExecutionContext: ExecutionContext,
      closeAfterUse: Boolean = true)(implicit F: Effect[F], ec: ExecutionContext): Sink[F, Byte] = {
    def write(os: OutputStream, buf: Chunk[Byte]) =
      blocking(writeBytesToOutputStream(os, buf), blockingExecutionContext)
    writeOutputStreamGeneric(fos, closeAfterUse, write)
  }

  // Copied from fs2.io.JavaInputOutputStream for visibility
  private def readBytesFromInputStream[F[_]](is: InputStream, buf: Array[Byte])(
      implicit F: Sync[F]): F[Option[Chunk[Byte]]] =
    F.delay(is.read(buf)).map { numBytes =>
      if (numBytes < 0) None
      else if (numBytes == 0) Some(Chunk.empty)
      else if (numBytes < buf.size) Some(Chunk.bytes(buf.slice(0, numBytes)))
      else Some(Chunk.bytes(buf))
    }

  // Copied from fs2.io.JavaInputOutputStream for visibility
  private def readInputStreamGeneric[F[_]](
      fis: F[InputStream],
      buf: F[Array[Byte]],
      f: (InputStream, Array[Byte]) => F[Option[Chunk[Byte]]],
      closeAfterUse: Boolean)(implicit F: Sync[F]): Stream[F, Byte] = {
    def useIs(is: InputStream) =
      Stream
        .eval(buf.flatMap(f(is, _)))
        .repeat
        .unNoneTerminate
        .flatMap(c => Stream.chunk(c))

    if (closeAfterUse)
      Stream.bracket(fis)(useIs, is => F.delay(is.close()))
    else
      Stream.eval(fis).flatMap(useIs)
  }

  // Copied from fs2.io.JavaInputOutputStream for visibility
  private def writeBytesToOutputStream[F[_]](os: OutputStream, bytes: Chunk[Byte])(
      implicit F: Sync[F]): F[Unit] =
    F.delay(os.write(bytes.toArray))

  // Copied from fs2.io.JavaInputOutputStream for visibility
  private def writeOutputStreamGeneric[F[_]](
      fos: F[OutputStream],
      closeAfterUse: Boolean,
      f: (OutputStream, Chunk[Byte]) => F[Unit])(implicit F: Sync[F]): Sink[F, Byte] = s => {
    def useOs(os: OutputStream): Stream[F, Unit] =
      s.chunks.evalMap(f(os, _))

    if (closeAfterUse)
      Stream.bracket(fos)(useOs, os => F.delay(os.close()))
    else
      Stream.eval(fos).flatMap(useOs)
  }

}
