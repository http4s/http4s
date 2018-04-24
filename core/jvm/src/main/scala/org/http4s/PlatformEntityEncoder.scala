package org.http4s

import java.io._
import java.nio.file.Path

import cats.effect.Sync
import cats.implicits._
import fs2.io._
import org.http4s.headers.`Transfer-Encoding`

trait PlatformEntityEncoderInstances {
  import EntityEncoder._

  protected[http4s] val DefaultChunkSize = 4096

//  // TODO parameterize chunk size
//  // TODO if Header moves to Entity, can add a Content-Disposition with the filename
  implicit def fileEncoder[F[_]](implicit F: Sync[F]): EntityEncoder[F, File] =
    filePathEncoder[F].contramap(_.toPath)

  // TODO parameterize chunk size
  // TODO if Header moves to Entity, can add a Content-Disposition with the filename
  implicit def filePathEncoder[F[_]: Sync]: EntityEncoder[F, Path] =
    encodeBy[F, Path](`Transfer-Encoding`(TransferCoding.chunked)) { p =>
      Entity(file.readAll[F](p, 4096)) //2 KB :P
    }

  // TODO parameterize chunk size
  implicit def inputStreamEncoder[F[_]: Sync, IS <: InputStream]: EntityEncoder[F, F[IS]] =
    entityBodyEncoder[F].contramap { in: F[IS] =>
      readInputStream[F](in.widen[InputStream], DefaultChunkSize)
    }
}
