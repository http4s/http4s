package org.http4s

import java.io.{File, FileOutputStream, PrintStream}

import cats.effect.Sync
import fs2.io._

/** Platform dependent EntityDecoder Instances
  */
trait PlatformEntityDecoderInstances {

  /////////////////// Instances //////////////////////////////////////////////

  // File operations // TODO: rewrite these using NIO non blocking FileChannels, and do these make sense as a 'decoder'?
  def binFile[F[_]](file: File)(implicit F: Sync[F]): EntityDecoder[F, File] =
    EntityDecoder.decodeBy(MediaRange.`*/*`) { msg =>
      val sink = writeOutputStream[F](F.delay(new FileOutputStream(file)))
      DecodeResult.success(msg.body.to(sink).run).map(_ => file)
    }

  def textFile[F[_]](file: File)(implicit F: Sync[F]): EntityDecoder[F, File] =
    EntityDecoder.decodeBy(MediaRange.`text/*`) { msg =>
      val sink = writeOutputStream[F](F.delay(new PrintStream(new FileOutputStream(file))))
      DecodeResult.success(msg.body.to(sink).run).map(_ => file)
    }

}
