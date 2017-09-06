package org.http4s

import java.io._
import java.nio.CharBuffer
import java.nio.file.Path

import cats._
import cats.effect.{Async, Sync}
import cats.functor._
import cats.implicits._
import fs2.Stream._
import fs2._
import fs2.io._
import org.http4s.headers._
import org.http4s.multipart.{Multipart, MultipartEncoder}
import org.http4s.syntax.async._

import scala.annotation.implicitNotFound
import scala.concurrent.{ExecutionContext, Future}

trait PlatformEntityEncoderInstances {
  import EntityEncoder._

  protected[http4s] val DefaultChunkSize = 4096

  // TODO parameterize chunk size
  // TODO if Header moves to Entity, can add a Content-Disposition with the filename
  implicit def fileEncoder[F[_]](implicit F: Sync[F]): EntityEncoder[F, File] =
    inputStreamEncoder[F, FileInputStream].contramap(file => F.delay(new FileInputStream(file)))

  // TODO parameterize chunk size
  // TODO if Header moves to Entity, can add a Content-Disposition with the filename
  implicit def filePathEncoder[F[_]: Sync]: EntityEncoder[F, Path] =
    fileEncoder[F].contramap(_.toFile)

  // TODO parameterize chunk size
  implicit def inputStreamEncoder[F[_]: Sync, IS <: InputStream]: EntityEncoder[F, F[IS]] =
    entityBodyEncoder[F].contramap { in: F[IS] =>
      readInputStream[F](in.widen[InputStream], DefaultChunkSize)
    }
}
