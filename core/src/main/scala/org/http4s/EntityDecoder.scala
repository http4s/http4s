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

import cats.{Applicative, Functor, Monad, SemigroupK}
import cats.effect.Concurrent
import cats.syntax.all._
import fs2._
import fs2.io.file.Files
import java.io.File
import org.http4s.multipart.{Multipart, MultipartDecoder}
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
  "Cannot decode into a value of type ${T}, because no EntityDecoder[${F}, ${T}] instance could be found.")
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

  def handleErrorWith(f: DecodeFailure => DecodeResult[F, T])(implicit
      F: Monad[F]): EntityDecoder[F, T] =
    transformWith {
      case Left(e) => f(e)
      case Right(r) => DecodeResult.successT(r)
    }

  def bimap[T2](f: DecodeFailure => DecodeFailure, s: T => T2)(implicit
      F: Functor[F]): EntityDecoder[F, T2] =
    transform {
      case Left(e) => Left(f(e))
      case Right(r) => Right(s(r))
    }

  def transform[T2](t: Either[DecodeFailure, T] => Either[DecodeFailure, T2])(implicit
      F: Functor[F]): EntityDecoder[F, T2] =
    new EntityDecoder[F, T2] {
      override def consumes: Set[MediaRange] = self.consumes

      override def decode(m: Media[F], strict: Boolean): DecodeResult[F, T2] =
        self.decode(m, strict).transform(t)
    }

  def biflatMap[T2](f: DecodeFailure => DecodeResult[F, T2], s: T => DecodeResult[F, T2])(implicit
      F: Monad[F]): EntityDecoder[F, T2] =
    transformWith {
      case Left(e) => f(e)
      case Right(r) => s(r)
    }

  def transformWith[T2](f: Either[DecodeFailure, T] => DecodeResult[F, T2])(implicit
      F: Monad[F]): EntityDecoder[F, T2] =
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
          b: EntityDecoder[F, T]): EntityDecoder[F, T] =
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
      f: Media[F] => DecodeResult[F, T]): EntityDecoder[F, T] =
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
    DecodeResult.success(m.body.chunks.compile.toVector.map(bytes => Chunk.concat(bytes)))

  /** Decodes a message to a String */
  def decodeText[F[_]](
      m: Media[F])(implicit F: Concurrent[F], defaultCharset: Charset = DefaultCharset): F[String] =
    m.bodyText.compile.string

  /////////////////// Instances //////////////////////////////////////////////

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

  implicit def text[F[_]](implicit
      F: Concurrent[F],
      defaultCharset: Charset = DefaultCharset): EntityDecoder[F, String] =
    EntityDecoder.decodeBy(MediaRange.`text/*`)(msg =>
      collectBinary(msg).map(chunk =>
        new String(chunk.toArray, msg.charset.getOrElse(defaultCharset).nioCharset)))

  implicit def charArrayDecoder[F[_]: Concurrent]: EntityDecoder[F, Array[Char]] =
    text.map(_.toArray)

  // File operations
  def binFile[F[_]: Files: Concurrent](file: File): EntityDecoder[F, File] =
    EntityDecoder.decodeBy(MediaRange.`*/*`) { msg =>
      val pipe = Files[F].writeAll(file.toPath)
      DecodeResult.success(msg.body.through(pipe).compile.drain).map(_ => file)
    }

  def textFile[F[_]: Files: Concurrent](file: File): EntityDecoder[F, File] =
    EntityDecoder.decodeBy(MediaRange.`text/*`) { msg =>
      val pipe = Files[F].writeAll(file.toPath)
      DecodeResult.success(msg.body.through(pipe).compile.drain).map(_ => file)
    }

  implicit def multipart[F[_]: Concurrent]: EntityDecoder[F, Multipart[F]] =
    MultipartDecoder.decoder

  def mixedMultipart[F[_]: Concurrent: Files](
      headerLimit: Int = 1024,
      maxSizeBeforeWrite: Int = 52428800,
      maxParts: Int = 50,
      failOnLimit: Boolean = false): EntityDecoder[F, Multipart[F]] =
    MultipartDecoder.mixedMultipart(headerLimit, maxSizeBeforeWrite, maxParts, failOnLimit)

  /** An entity decoder that ignores the content and returns unit. */
  implicit def void[F[_]: Concurrent]: EntityDecoder[F, Unit] =
    EntityDecoder.decodeBy(MediaRange.`*/*`) { msg =>
      DecodeResult.success(msg.body.drain.compile.drain)
    }
}
