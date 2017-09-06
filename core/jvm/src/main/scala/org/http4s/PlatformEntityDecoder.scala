package org.http4s

import java.io.{File, FileOutputStream, PrintStream}

import cats._
import cats.effect.Sync
import cats.implicits._
import fs2._
import fs2.io._
import org.http4s.headers.`Content-Type`
import org.http4s.multipart.{Multipart, MultipartDecoder}
import org.http4s.util.chunk._

import scala.annotation.implicitNotFound
import scala.util.control.NonFatal

/** Platform dependent EntityDecoder Instances
  */
trait PlatformEntityDecoderInstances {
  import org.http4s.EntityDecoder._

  /////////////////// Instances //////////////////////////////////////////////

  // File operations // TODO: rewrite these using NIO non blocking FileChannels, and do these make sense as a 'decoder'?
  def binFile[F[_]: MonadError[?[_], Throwable]](file: File)(
      implicit F: Sync[F]): EntityDecoder[F, File] =
    EntityDecoder.decodeBy(MediaRange.`*/*`) { msg =>
      val sink = writeOutputStream[F](F.delay(new FileOutputStream(file)))
      DecodeResult.success(msg.body.to(sink).run).map(_ => file)
    }

  def textFile[F[_]: MonadError[?[_], Throwable]](file: File)(
      implicit F: Sync[F]): EntityDecoder[F, File] =
    EntityDecoder.decodeBy(MediaRange.`text/*`) { msg =>
      val sink = writeOutputStream[F](F.delay(new PrintStream(new FileOutputStream(file))))
      DecodeResult.success(msg.body.to(sink).run).map(_ => file)
    }
}
