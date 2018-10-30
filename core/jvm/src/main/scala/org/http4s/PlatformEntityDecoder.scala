package org.http4s

import java.io.File

import cats.effect.{ContextShift, Sync}
import fs2.io.file.writeAll
import java.io.File
import scala.concurrent.ExecutionContext

/** Platform dependent EntityDecoder Instances
  */
trait PlatformEntityDecoderInstances {

  // File operations
  def binFile[F[_]](file: File, blockingExecutionContext: ExecutionContext)(
      implicit F: Sync[F],
      cs: ContextShift[F]): EntityDecoder[F, File] =
    EntityDecoder.decodeBy(MediaRange.`*/*`) { msg =>
      val sink = writeAll[F](file.toPath, blockingExecutionContext)
      DecodeResult.success(msg.body.to(sink).compile.drain).map(_ => file)
    }

  def textFile[F[_]](file: File, blockingExecutionContext: ExecutionContext)(
      implicit F: Sync[F],
      cs: ContextShift[F]): EntityDecoder[F, File] =
    EntityDecoder.decodeBy(MediaRange.`text/*`) { msg =>
      val sink = writeAll[F](file.toPath, blockingExecutionContext)
      DecodeResult.success(msg.body.to(sink).compile.drain).map(_ => file)
    }

}
