package org.http4s

import cats._
import cats.effect.{ContextShift, Sync}
import cats.implicits._
import fs2._
import fs2.io.file.writeAll
import java.io.File
import org.http4s.headers.`Content-Type`
import org.http4s.multipart.{Multipart, MultipartDecoder}
import scala.annotation.implicitNotFound
import scala.concurrent.ExecutionContext

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
  def decode(msg: Message[F], strict: Boolean): DecodeResult[F, T]

  /** The [[MediaRange]]s this [[EntityDecoder]] knows how to handle */
  def consumes: Set[MediaRange]

  /** Make a new [[EntityDecoder]] by mapping the output result */
  def map[T2](f: T => T2)(implicit F: Functor[F]): EntityDecoder[F, T2] = new EntityDecoder[F, T2] {
    override def consumes: Set[MediaRange] = self.consumes

    override def decode(msg: Message[F], strict: Boolean): DecodeResult[F, T2] =
      self.decode(msg, strict).map(f)
  }

  def flatMapR[T2](f: T => DecodeResult[F, T2])(implicit F: Monad[F]): EntityDecoder[F, T2] =
    new EntityDecoder[F, T2] {
      override def decode(msg: Message[F], strict: Boolean): DecodeResult[F, T2] =
        self.decode(msg, strict).flatMap(f)

      override def consumes: Set[MediaRange] = self.consumes
    }

  def handleError(f: DecodeFailure => T)(implicit F: Functor[F]): EntityDecoder[F, T] = transform {
    case Left(e) => Right(f(e))
    case r @ Right(_) => r
  }

  def handleErrorWith(f: DecodeFailure => DecodeResult[F, T])(
      implicit F: Monad[F]): EntityDecoder[F, T] = transformWith {
    case Left(e) => f(e)
    case Right(r) => DecodeResult.success(r)
  }

  def bimap[T2](f: DecodeFailure => DecodeFailure, s: T => T2)(
      implicit F: Functor[F]): EntityDecoder[F, T2] =
    transform {
      case Left(e) => Left(f(e))
      case Right(r) => Right(s(r))
    }

  def transform[T2](t: Either[DecodeFailure, T] => Either[DecodeFailure, T2])(
      implicit F: Functor[F]): EntityDecoder[F, T2] =
    new EntityDecoder[F, T2] {
      override def consumes: Set[MediaRange] = self.consumes

      override def decode(msg: Message[F], strict: Boolean): DecodeResult[F, T2] =
        self.decode(msg, strict).transform(t)
    }

  def biflatMap[T2](f: DecodeFailure => DecodeResult[F, T2], s: T => DecodeResult[F, T2])(
      implicit F: Monad[F]): EntityDecoder[F, T2] =
    transformWith {
      case Left(e) => f(e)
      case Right(r) => s(r)
    }

  def transformWith[T2](f: Either[DecodeFailure, T] => DecodeResult[F, T2])(
      implicit F: Monad[F]): EntityDecoder[F, T2] =
    new EntityDecoder[F, T2] {
      override def consumes: Set[MediaRange] = self.consumes

      override def decode(msg: Message[F], strict: Boolean): DecodeResult[F, T2] =
        DecodeResult(
          F.flatMap(self.decode(msg, strict).value)(r => f(r).value)
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

  implicit def semigroupKForEntityDecoder[F[_]: Functor]: SemigroupK[EntityDecoder[F, ?]] =
    new SemigroupK[EntityDecoder[F, ?]] {
      override def combineK[T](
          a: EntityDecoder[F, T],
          b: EntityDecoder[F, T]): EntityDecoder[F, T] = new EntityDecoder[F, T] {

        override def decode(msg: Message[F], strict: Boolean): DecodeResult[F, T] =
          msg.headers.get(`Content-Type`) match {
            case Some(contentType) =>
              if (a.matchesMediaType(contentType.mediaType)) {
                a.decode(msg, strict)
              } else
                b.decode(msg, strict).leftMap {
                  case MediaTypeMismatch(actual, expected) =>
                    MediaTypeMismatch(actual, expected ++ a.consumes)
                  case other => other
                }

            case None =>
              if (a.matchesMediaType(UndefinedMediaType)) {
                a.decode(msg, strict)
              } else
                b.decode(msg, strict).leftMap {
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
    * Exceptions thrown by `f` are not caught.  Care should be taken
    * that recoverable errors are returned as a
    * [[DecodeResult.failure]], or that system errors are raised in `F`.
    */
  def decodeBy[F[_]: Applicative, T](r1: MediaRange, rs: MediaRange*)(
      f: Message[F] => DecodeResult[F, T]): EntityDecoder[F, T] = new EntityDecoder[F, T] {
    override def decode(msg: Message[F], strict: Boolean): DecodeResult[F, T] =
      if (strict) {
        msg.headers.get(`Content-Type`) match {
          case Some(c) if matchesMediaType(c.mediaType) => f(msg)
          case Some(c) => DecodeResult.failure(MediaTypeMismatch(c.mediaType, consumes))
          case None if matchesMediaType(UndefinedMediaType) => f(msg)
          case None => DecodeResult.failure(MediaTypeMissing(consumes))
        }
      } else {
        f(msg)
      }

    override val consumes: Set[MediaRange] = (r1 +: rs).toSet
  }

  /** Helper method which simply gathers the body into a single Chunk */
  def collectBinary[F[_]: Sync](msg: Message[F]): DecodeResult[F, Chunk[Byte]] =
    DecodeResult.success(msg.body.chunks.compile.toVector.map(Chunk.concatBytes))

  /** Decodes a message to a String */
  def decodeString[F[_]: Sync](msg: Message[F])(
      implicit defaultCharset: Charset = DefaultCharset): F[String] =
    msg.bodyAsText.compile.foldMonoid

  /////////////////// Instances //////////////////////////////////////////////

  /** Provides a mechanism to fail decoding */
  def error[F[_], T](t: Throwable)(implicit F: Sync[F]): EntityDecoder[F, T] =
    new EntityDecoder[F, T] {
      override def decode(msg: Message[F], strict: Boolean): DecodeResult[F, T] =
        DecodeResult(msg.body.compile.drain *> F.raiseError(t))
      override def consumes: Set[MediaRange] = Set.empty
    }

  implicit def binary[F[_]: Sync]: EntityDecoder[F, Chunk[Byte]] =
    EntityDecoder.decodeBy(MediaRange.`*/*`)(collectBinary[F])

  @deprecated("Use `binary` instead", "0.19.0-M2")
  def binaryChunk[F[_]: Sync]: EntityDecoder[F, Chunk[Byte]] =
    binary[F]

  implicit def byteArrayDecoder[F[_]: Sync]: EntityDecoder[F, Array[Byte]] =
    binary.map(_.toArray)

  implicit def text[F[_]: Sync](
      implicit defaultCharset: Charset = DefaultCharset): EntityDecoder[F, String] =
    EntityDecoder.decodeBy(MediaRange.`text/*`)(msg =>
      collectBinary(msg).map(chunk =>
        new String(chunk.toArray, msg.charset.getOrElse(defaultCharset).nioCharset)))

  implicit def charArrayDecoder[F[_]: Sync]: EntityDecoder[F, Array[Char]] =
    text.map(_.toArray)

  // File operations
  def binFile[F[_]](file: File, blockingExecutionContext: ExecutionContext)(
      implicit F: Sync[F],
      cs: ContextShift[F]): EntityDecoder[F, File] =
    EntityDecoder.decodeBy(MediaRange.`*/*`) { msg =>
      val pipe = writeAll[F](file.toPath, blockingExecutionContext)
      DecodeResult.success(msg.body.through(pipe).compile.drain).map(_ => file)
    }

  def textFile[F[_]](file: File, blockingExecutionContext: ExecutionContext)(
      implicit F: Sync[F],
      cs: ContextShift[F]): EntityDecoder[F, File] =
    EntityDecoder.decodeBy(MediaRange.`text/*`) { msg =>
      val pipe = writeAll[F](file.toPath, blockingExecutionContext)
      DecodeResult.success(msg.body.through(pipe).compile.drain).map(_ => file)
    }

  implicit def multipart[F[_]: Sync]: EntityDecoder[F, Multipart[F]] =
    MultipartDecoder.decoder

  /** An entity decoder that ignores the content and returns unit. */
  implicit def void[F[_]: Sync]: EntityDecoder[F, Unit] =
    EntityDecoder.decodeBy(MediaRange.`*/*`) { msg =>
      DecodeResult.success(msg.body.drain.compile.drain)
    }
}
