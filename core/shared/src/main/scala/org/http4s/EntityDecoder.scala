/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s

import cats.Applicative
import cats.Functor
import cats.Monad
import cats.SemigroupK
import cats.effect.Concurrent
import cats.effect.Resource
import cats.syntax.all._
import fs2._
import fs2.io.file.Files
import fs2.io.file.Flags
import fs2.io.file.Path
import org.http4s.Charset.`UTF-8`
import org.http4s.multipart.Multipart
import org.http4s.multipart.MultipartDecoder
import scodec.bits.ByteVector

import java.io.File
import scala.annotation.implicitNotFound

/** A type that can be used to decode a [[Message]]
  * EntityDecoder is used to attempt to decode a [[Message]] returning the
  * entire resulting A. If an error occurs it will result in a failed effect.
  * The default decoders provided here are not streaming, but one could implement
  * a streaming decoder by having the value of A be some kind of streaming construct.
  *
  * @tparam T result type produced by the decoder
  */
@implicitNotFound(
  "Cannot decode into a value of type ${T}, because no EntityDecoder[${F}, ${T}] instance could be found."
)
trait EntityDecoder[F[_], T] { self =>

  /** Attempt to decode the body of the [[Message]] */
  def decode(m: Media[F], strict: Boolean): DecodeResult[F, T]

  /** The [[MediaRange]]s this [[EntityDecoder]] knows how to handle */
  def consumes: Set[MediaRange]

  /** Make a new [[EntityDecoder]] by mapping the output result */
  def map[T2](f: T => T2)(implicit F: Functor[F]): EntityDecoder[F, T2] =
    new EntityDecoder[F, T2] {
      override def consumes: Set[MediaRange] = self.consumes

      override def decode(m: Media[F], strict: Boolean): DecodeResult[F, T2] =
        self.decode(m, strict).map(f)
    }

  def flatMapR[T2](f: T => DecodeResult[F, T2])(implicit F: Monad[F]): EntityDecoder[F, T2] =
    new EntityDecoder[F, T2] {
      override def decode(m: Media[F], strict: Boolean): DecodeResult[F, T2] =
        self.decode(m, strict).flatMap(f)

      override def consumes: Set[MediaRange] = self.consumes
    }

  def handleError(f: DecodeFailure => T)(implicit F: Functor[F]): EntityDecoder[F, T] =
    transform {
      case Left(e) => Right(f(e))
      case r @ Right(_) => r
    }

  def handleErrorWith(
      f: DecodeFailure => DecodeResult[F, T]
  )(implicit F: Monad[F]): EntityDecoder[F, T] =
    transformWith {
      case Left(e) => f(e)
      case Right(r) => DecodeResult.successT(r)
    }

  def bimap[T2](f: DecodeFailure => DecodeFailure, s: T => T2)(implicit
      F: Functor[F]
  ): EntityDecoder[F, T2] =
    transform {
      case Left(e) => Left(f(e))
      case Right(r) => Right(s(r))
    }

  def transform[T2](
      t: Either[DecodeFailure, T] => Either[DecodeFailure, T2]
  )(implicit F: Functor[F]): EntityDecoder[F, T2] =
    new EntityDecoder[F, T2] {
      override def consumes: Set[MediaRange] = self.consumes

      override def decode(m: Media[F], strict: Boolean): DecodeResult[F, T2] =
        self.decode(m, strict).transform(t)
    }

  def biflatMap[T2](f: DecodeFailure => DecodeResult[F, T2], s: T => DecodeResult[F, T2])(implicit
      F: Monad[F]
  ): EntityDecoder[F, T2] =
    transformWith {
      case Left(e) => f(e)
      case Right(r) => s(r)
    }

  def transformWith[T2](
      f: Either[DecodeFailure, T] => DecodeResult[F, T2]
  )(implicit F: Monad[F]): EntityDecoder[F, T2] =
    new EntityDecoder[F, T2] {
      override def consumes: Set[MediaRange] = self.consumes

      override def decode(m: Media[F], strict: Boolean): DecodeResult[F, T2] =
        DecodeResult(
          F.flatMap(self.decode(m, strict).value)(r => f(r).value)
        )
    }

  /** Combine two [[EntityDecoder]]'s
    *
    * The new [[EntityDecoder]] will first attempt to determine if it can perform the decode,
    * and if not, defer to the second [[EntityDecoder]]
    *
    * @param other backup [[EntityDecoder]]
    */
  def orElse[T2 >: T](other: EntityDecoder[F, T2])(implicit F: Functor[F]): EntityDecoder[F, T2] =
    widen[T2] <+> other

  /** true if this [[EntityDecoder]] knows how to decode the provided [[MediaType]] */
  def matchesMediaType(mediaType: MediaType): Boolean =
    consumes.exists(_.satisfiedBy(mediaType))

  def widen[T2 >: T]: EntityDecoder[F, T2] =
    this.asInstanceOf[EntityDecoder[F, T2]]
}

/** EntityDecoder is used to attempt to decode an [[EntityBody]]
  * This companion object provides a way to create `new EntityDecoder`s along
  * with some commonly used instances which can be resolved implicitly.
  */
object EntityDecoder {
  // This is not a real media type but will still be matched by `*/*`
  private val UndefinedMediaType = new MediaType("UNKNOWN", "UNKNOWN")

  /** summon an implicit [[EntityDecoder]] */
  def apply[F[_], T](implicit ev: EntityDecoder[F, T]): EntityDecoder[F, T] = ev

  implicit def semigroupKForEntityDecoder[F[_]: Functor]: SemigroupK[EntityDecoder[F, *]] =
    new SemigroupK[EntityDecoder[F, *]] {
      override def combineK[T](
          a: EntityDecoder[F, T],
          b: EntityDecoder[F, T],
      ): EntityDecoder[F, T] =
        new EntityDecoder[F, T] {
          override def decode(m: Media[F], strict: Boolean): DecodeResult[F, T] = {
            val mediaType = m.contentType.fold(UndefinedMediaType)(_.mediaType)

            if (a.matchesMediaType(mediaType))
              a.decode(m, strict)
            else
              b.decode(m, strict).leftMap {
                case MediaTypeMismatch(actual, expected) =>
                  MediaTypeMismatch(actual, expected ++ a.consumes)
                case MediaTypeMissing(expected) =>
                  MediaTypeMissing(expected ++ a.consumes)
                case other => other
              }
          }

          override def consumes: Set[MediaRange] = a.consumes ++ b.consumes
        }
    }

  /** Create a new [[EntityDecoder]]
    *
    * The new [[EntityDecoder]] will attempt to decode messages of type `T`
    * only if the [[Message]] satisfies the provided [[MediaRange]].
    *
    * Exceptions thrown by `f` are not caught.  Care should be taken that
    * recoverable errors are returned as a [[DecodeResult#failure]], or that
    * system errors are raised in `F`.
    */
  def decodeBy[F[_]: Applicative, T](r1: MediaRange, rs: MediaRange*)(
      f: Media[F] => DecodeResult[F, T]
  ): EntityDecoder[F, T] =
    new EntityDecoder[F, T] {
      override def decode(m: Media[F], strict: Boolean): DecodeResult[F, T] =
        if (strict)
          m.contentType match {
            case Some(c) if matchesMediaType(c.mediaType) => f(m)
            case Some(c) => DecodeResult.failureT(MediaTypeMismatch(c.mediaType, consumes))
            case None if matchesMediaType(UndefinedMediaType) => f(m)
            case None => DecodeResult.failureT(MediaTypeMissing(consumes))
          }
        else
          f(m)

      override val consumes: Set[MediaRange] = (r1 +: rs).toSet
    }

  /** Helper method which simply gathers the body into a single Chunk */
  def collectBinary[F[_]: Concurrent](m: Media[F]): DecodeResult[F, Chunk[Byte]] =
    DecodeResult.success(m.body.chunks.compile.to(Chunk).map(_.flatten))

  /** Helper method which simply gathers the body into a single ByteVector */
  private def collectByteVector[F[_]: Concurrent](m: Media[F]): DecodeResult[F, ByteVector] =
    DecodeResult.success(m.body.compile.to(ByteVector))

  /** Decodes a message to a String */
  def decodeText[F[_]](
      m: Media[F]
  )(implicit F: Concurrent[F], defaultCharset: Charset = `UTF-8`): F[String] =
    m.bodyText.compile.string

  // ///////////////// Instances //////////////////////////////////////////////

  /** Provides a mechanism to fail decoding */
  def error[F[_], T](t: Throwable)(implicit F: Concurrent[F]): EntityDecoder[F, T] =
    new EntityDecoder[F, T] {
      override def decode(m: Media[F], strict: Boolean): DecodeResult[F, T] =
        DecodeResult(m.body.compile.drain *> F.raiseError(t))
      override def consumes: Set[MediaRange] = Set.empty
    }

  implicit def binary[F[_]: Concurrent]: EntityDecoder[F, Chunk[Byte]] =
    EntityDecoder.decodeBy(MediaRange.`*/*`)(collectBinary[F])

  implicit def byteArrayDecoder[F[_]: Concurrent]: EntityDecoder[F, Array[Byte]] =
    binary.map(_.toArray)

  implicit def byteVector[F[_]: Concurrent]: EntityDecoder[F, ByteVector] =
    EntityDecoder.decodeBy(MediaRange.`*/*`)(collectByteVector[F])

  implicit def text[F[_]](implicit
      F: Concurrent[F],
      defaultCharset: Charset = `UTF-8`,
  ): EntityDecoder[F, String] =
    EntityDecoder.decodeBy(MediaRange.`text/*`)(msg =>
      collectBinary(msg).map(chunk =>
        new String(chunk.toArray, msg.charset.getOrElse(defaultCharset).nioCharset)
      )
    )

  implicit def charArrayDecoder[F[_]: Concurrent]: EntityDecoder[F, Array[Char]] =
    text.map(_.toArray)

  // File operations
  @deprecated("Use overload with fs2.io.file.Path", "0.23.5")
  def binFile[F[_]: Files: Concurrent](file: File): EntityDecoder[F, File] =
    binFile(Path.fromNioPath(file.toPath())).map(_ => file)

  @deprecated("Use overload with fs2.io.file.Path", "0.23.5")
  def textFile[F[_]: Files: Concurrent](file: File): EntityDecoder[F, File] =
    textFile(Path.fromNioPath(file.toPath())).map(_ => file)

  def binFile[F[_]: Files: Concurrent](path: Path): EntityDecoder[F, Path] =
    binFileImpl(path, Files[F].writeAll(path))

  def binFile[F[_]: Files: Concurrent](path: Path, flags: Flags): EntityDecoder[F, Path] =
    binFileImpl(path, Files[F].writeAll(path, flags))

  private[this] def binFileImpl[F[_]: Concurrent](
      path: Path,
      pipe: Pipe[F, Byte, Nothing],
  ): EntityDecoder[F, Path] =
    EntityDecoder.decodeBy(MediaRange.`*/*`) { msg =>
      DecodeResult.success(msg.body.through(pipe).compile.drain).as(path)
    }

  def textFile[F[_]: Files: Concurrent](path: Path): EntityDecoder[F, Path] =
    textFileImpl(path, Files[F].writeAll(path))

  def textFile[F[_]: Files: Concurrent](path: Path, flags: Flags): EntityDecoder[F, Path] =
    textFileImpl(path, Files[F].writeAll(path, flags))

  private[this] def textFileImpl[F[_]: Concurrent](
      path: Path,
      pipe: Pipe[F, Byte, Nothing],
  ): EntityDecoder[F, Path] =
    EntityDecoder.decodeBy(MediaRange.`text/*`) { msg =>
      DecodeResult.success(msg.body.through(pipe).compile.drain).as(path)
    }

  implicit def multipart[F[_]: Concurrent]: EntityDecoder[F, Multipart[F]] =
    MultipartDecoder.decoder

  /** Multipart decoder that streams all parts past a threshold
    * (anything above `maxSizeBeforeWrite`) into a temporary file.
    * The decoder is only valid inside the `Resource` scope; once
    * the `Resource` is released, all the created files are deleted.
    *
    * Note that no files are deleted until the `Resource` is released.
    * Thus, sharing and reusing the resulting `EntityDecoder` is not
    * recommended, and can lead to disk space leaks.
    *
    * The intended way to use this is as follows:
    *
    * {{{
    * mixedMultipartResource[F]()
    *   .flatTap(request.decodeWith(_, strict = true))
    *   .use { multipart =>
    *     // Use the decoded entity
    *   }
    * }}}
    *
    * @param headerLimit the max size for the headers, in bytes. This is required as
    *                    headers are strictly evaluated and parsed.
    * @param maxSizeBeforeWrite the maximum size of a particular part before writing to a file is triggered
    * @param maxParts the maximum number of parts this decoder accepts. NOTE: this also may mean that a body that doesn't
    *                 conform perfectly to the spec (i.e isn't terminated properly) but has a lot of parts might
    *                 be parsed correctly, despite the total body being malformed due to not conforming to the multipart
    *                 spec. You can control this by `failOnLimit`, by setting it to true if you want to raise
    *                 an error if sending too many parts to a particular endpoint
    * @param failOnLimit Fail if `maxParts` is exceeded _during_ multipart parsing.
    * @param chunkSize the size of chunks created when reading data from temporary files.
    * @return A supervised multipart decoder.
    */
  def mixedMultipartResource[F[_]: Concurrent: Files](
      headerLimit: Int = 1024,
      maxSizeBeforeWrite: Int = 52428800,
      maxParts: Int = 50,
      failOnLimit: Boolean = false,
      chunkSize: Int = 8192,
  ): Resource[F, EntityDecoder[F, Multipart[F]]] =
    MultipartDecoder.mixedMultipartResource(
      headerLimit,
      maxSizeBeforeWrite,
      maxParts,
      failOnLimit,
      chunkSize,
    )

  /** Multipart decoder that streams all parts past a threshold
    * (anything above maxSizeBeforeWrite) into a temporary file.
    *
    * Note: (BIG NOTE) Using this decoder for multipart decoding is good for the sake of
    * not holding all information in memory, as it will never have more than
    * `maxSizeBeforeWrite` in memory before writing to a temporary file. On top of this,
    * you can gate the # of parts to further stop the quantity of parts you can have.
    * That said, because after a threshold it writes into a temporary file, given
    * bincompat reasons on 0.18.x, there is no way to make a distinction about which `Part[F]`
    * is a stream reference to a file or not. Thus, consumers using this decoder
    * should drain all `Part[F]` bodies if they were decoded correctly. That said,
    * this decoder gives you more control about how many part bodies it parses in the first place, thus you can have
    * more fine-grained control about how many parts you accept.
    *
    * @param headerLimit the max size for the headers, in bytes. This is required as
    *                    headers are strictly evaluated and parsed.
    * @param maxSizeBeforeWrite the maximum size of a particular part before writing to a file is triggered
    * @param maxParts the maximum number of parts this decoder accepts. NOTE: this also may mean that a body that doesn't
    *                 conform perfectly to the spec (i.e isn't terminated properly) but has a lot of parts might
    *                 be parsed correctly, despite the total body being malformed due to not conforming to the multipart
    *                 spec. You can control this by `failOnLimit`, by setting it to true if you want to raise
    *                 an error if sending too many parts to a particular endpoint
    * @param failOnLimit Fail if `maxParts` is exceeded _during_ multipart parsing.
    * @return A multipart/form-data encoded vector of parts with some part bodies held in
    *         temporary files.
    */
  @deprecated("Use mixedMultipartResource", "0.23")
  def mixedMultipart[F[_]: Concurrent: Files](
      headerLimit: Int = 1024,
      maxSizeBeforeWrite: Int = 52428800,
      maxParts: Int = 50,
      failOnLimit: Boolean = false,
  ): EntityDecoder[F, Multipart[F]] =
    MultipartDecoder.mixedMultipart(headerLimit, maxSizeBeforeWrite, maxParts, failOnLimit)

  @deprecated("Broken. An entity decoder cannot return a Stream", "0.23.17")
  implicit def eventStream[F[_]: Applicative]: EntityDecoder[F, EventStream[F]] =
    EntityDecoder.decodeBy(MediaType.`text/event-stream`) { msg =>
      DecodeResult.successT(msg.body.through(ServerSentEvent.decoder))
    }

  /** An entity decoder that ignores the content and returns unit. */
  implicit def void[F[_]: Concurrent]: EntityDecoder[F, Unit] =
    EntityDecoder.decodeBy(MediaRange.`*/*`) { msg =>
      DecodeResult.success(msg.body.compile.drain)
    }
}
