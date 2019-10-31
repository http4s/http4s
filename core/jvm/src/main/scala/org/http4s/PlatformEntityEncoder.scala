package org.http4s

import java.io._
import java.nio.file.Path

import cats.effect.Sync
import cats.effect.{ContextShift, Effect, Sync}
import cats.implicits._
import fs2.io.file.readAll
import fs2.io.readInputStream
import java.nio.file.Path
import org.http4s.headers.`Transfer-Encoding`
import scala.concurrent.ExecutionContext

trait PlatformEntityEncoderInstances {
  import EntityEncoder._

  protected[http4s] val DefaultChunkSize = 4096

  // TODO parameterize chunk size
  // TODO if Header moves to Entity, can add a Content-Disposition with the filename
  def fileEncoder[F[_]](blockingExecutionContext: ExecutionContext)(
      implicit F: Effect[F],
      cs: ContextShift[F]): EntityEncoder[F, File] =
    filePathEncoder[F](blockingExecutionContext).contramap(_.toPath)

  // TODO parameterize chunk size
  // TODO if Header moves to Entity, can add a Content-Disposition with the filename
  def filePathEncoder[F[_]: Sync: ContextShift](
      blockingExecutionContext: ExecutionContext): EntityEncoder[F, Path] =
    encodeBy[F, Path](`Transfer-Encoding`(TransferCoding.chunked)) { p =>
      Entity(readAll[F](p, blockingExecutionContext, 4096)) //2 KB :P
    }

  // TODO parameterize chunk size
  def inputStreamEncoder[F[_]: Sync: ContextShift, IS <: InputStream](
      blockingExecutionContext: ExecutionContext): EntityEncoder[F, F[IS]] =
    entityBodyEncoder[F].contramap { in: F[IS] =>
      readInputStream[F](in.widen[InputStream], DefaultChunkSize, blockingExecutionContext)
    }

}
