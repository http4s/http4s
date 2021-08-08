package org.http4s
package dom

import cats.effect.kernel.Async
import org.scalajs.dom.File
import org.scalajs.dom.experimental.ReadableStream
import fs2.Stream

import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array

object DomEntityEncoder {

  def fileEncoder[F[_]](implicit F: Async[F]): EntityEncoder[F, File] =
    EntityEncoder.entityBodyEncoder.contramap { file =>
      Stream
        .bracketCase {
          // Unfortunately stream() method is missing from the File facade
          F.delay(file.asInstanceOf[js.Dynamic].stream().asInstanceOf[ReadableStream[Uint8Array]])
        } { case (rs, exitCase) => closeReadableStream(rs, exitCase) }
        .flatMap(readableStreamToStream[F])
    }

  def readableStreamEncoder[F[_]: Async]: EntityEncoder[F, ReadableStream[Uint8Array]] =
    EntityEncoder.entityBodyEncoder.contramap { rs =>
      readableStreamToStream(rs)
    }

}
