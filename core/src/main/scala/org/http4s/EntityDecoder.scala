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

/** A type that can be used to decode a [[Message]]
  * EntityDecoder is used to attempt to decode a [[Message]] returning the
  * entire resulting A. If an error occurs it will result in a failed effect.
  * The default decoders provided here are not streaming, but one could implement
  * a streaming decoder by having the value of A be some kind of streaming construct.
  * @tparam T result type produced by the decoder
  */
@implicitNotFound(
  "Cannot decode into a value of type ${T}, because no EntityDecoder[${F}, ${T}] instance could be found.")
trait EntityDecoder[F[_], T, M[_[_]]] { self =>

  /** Attempt to decode the body of the [[Message]] */
  def decode(msg: M[F], strict: Boolean)(implicit M: Message[F, M]): DecodeResult[F, T]

  /** The [[MediaRange]]s this [[EntityDecoder]] knows how to handle */
  def consumes: Set[MediaRange]

  /** Make a new [[EntityDecoder]] by mapping the output result */
  def map[T2](f: T => T2)(implicit F: Functor[F]): EntityDecoder[F, T2, M] =
    new EntityDecoder[F, T2, M] {
      override def consumes: Set[MediaRange] = self.consumes

      override def decode(msg: M[F], strict: Boolean)(
          implicit M: Message[F, M]): DecodeResult[F, T2] =
        self.decode(msg, strict).map(f)
    }

  def flatMapR[T2](f: T => DecodeResult[F, T2])(implicit F: Monad[F]): EntityDecoder[F, T2, M] =
    new EntityDecoder[F, T2, M] {
      override def decode(msg: M[F], strict: Boolean)(
          implicit M: Message[F, M]): DecodeResult[F, T2] =
        self.decode(msg, strict).flatMap(f)

      override def consumes: Set[MediaRange] = self.consumes
    }

  /** Combine two [[EntityDecoder]]'s
    *
    * The new [[EntityDecoder]] will first attempt to determine if it can perform the decode,
    * and if not, defer to the second [[EntityDecoder]]
    * @param other backup [[EntityDecoder]]
    */
  def orElse[T2 >: T](other: EntityDecoder[F, T2, M])(
      implicit F: Functor[F]): EntityDecoder[F, T2, M] =
    new EntityDecoder.OrDec(widen[T2], other)

  /** true if this [[EntityDecoder]] knows how to decode the provided [[MediaType]] */
  def matchesMediaType(mediaType: MediaType): Boolean =
    consumes.exists(_.satisfiedBy(mediaType))

  def widen[T2 >: T]: EntityDecoder[F, T2, M] =
    this.asInstanceOf[EntityDecoder[F, T2, M]]
}

/** EntityDecoder is used to attempt to decode an [[EntityBody]]
  * This companion object provides a way to create `new EntityDecoder`s along
  * with some commonly used instances which can be resolved implicitly.
  */
object EntityDecoder extends EntityDecoderInstances {

  // This is not a real media type but will still be matched by `*/*`
  private val UndefinedMediaType = new MediaType("UNKNOWN", "UNKNOWN")

  /** summon an implicit [[EntityEncoder]] */
  def apply[F[_], T, M[_[_]]](implicit ev: EntityDecoder[F, T, M]): EntityDecoder[F, T, M] = ev

  /** Create a new [[EntityDecoder]]
    *
    * The new [[EntityDecoder]] will attempt to decode messages of type `T`
    * only if the [[Message]] satisfies the provided [[MediaRange]]s
    */
  def decodeBy[F[_]: Applicative, M[_[_]], T](r1: MediaRange, rs: MediaRange*)(
      f: M[F] => DecodeResult[F, T]): EntityDecoder[F, T, M] = new EntityDecoder[F, T, M] {
    override def decode(msg: M[F], strict: Boolean)(implicit M: Message[F, M]): DecodeResult[F, T] =
      try {
        if (strict) {
          M.headers(msg).get(`Content-Type`) match {
            case Some(c) if matchesMediaType(c.mediaType) => f(msg)
            case Some(c) => DecodeResult.failure(MediaTypeMismatch(c.mediaType, consumes))
            case None if matchesMediaType(UndefinedMediaType) => f(msg)
            case None => DecodeResult.failure(MediaTypeMissing(consumes))
          }
        } else {
          f(msg)
        }
      } catch {
        case NonFatal(e) =>
          DecodeResult.failure(MalformedMessageBodyFailure("Error decoding body", Some(e)))
      }

    override val consumes: Set[MediaRange] = (r1 +: rs).toSet
  }

  private class OrDec[F[_]: Functor, T, M[_[_]]](
      a: EntityDecoder[F, T, M],
      b: EntityDecoder[F, T, M])
      extends EntityDecoder[F, T, M] {
    override def decode(msg: M[F], strict: Boolean)(implicit M: Message[F, M]): DecodeResult[F, T] =
      M.headers(msg).get(`Content-Type`) match {
        case Some(contentType) =>
          if (a.matchesMediaType(contentType.mediaType)) {
            a.decode(msg, strict)
          } else {
            b.decode(msg, strict).leftMap {
              case MediaTypeMismatch(actual, expected) =>
                MediaTypeMismatch(actual, expected ++ a.consumes)
              case other => other
            }
          }

        case None =>
          if (a.matchesMediaType(UndefinedMediaType)) {
            a.decode(msg, strict)
          } else {
            b.decode(msg, strict).leftMap {
              case MediaTypeMissing(expected) =>
                MediaTypeMissing(expected ++ a.consumes)
              case other => other
            }
          }
      }

    override val consumes: Set[MediaRange] = a.consumes ++ b.consumes
  }

  /** Helper method which simply gathers the body into a single ByteVector */
  def collectBinary[F[_]: Sync, M[_[_]]](msg: M[F])(
      implicit M: Message[F, M[F]]): DecodeResult[F, Chunk[Byte]] =
    DecodeResult.success(M.body(msg).chunks.runFoldMonoid)

  /** Decodes a message to a String */
  def decodeString[F[_]: Sync, M[_[_]]](msg: M[F])(
      implicit M: Message[F, M[F]],
      defaultCharset: Charset = DefaultCharset): F[String] =
    M.bodyAsText(msg).runFoldMonoid
}

/** Implementations of the EntityDecoder instances */
trait EntityDecoderInstances {
  import org.http4s.EntityDecoder._

  /////////////////// Instances //////////////////////////////////////////////

  /** Provides a mechanism to fail decoding */
  def error[F[_], T, M[_[_]]](t: Throwable)(implicit F: Sync[F]): EntityDecoder[F, T, M] =
    new EntityDecoder[F, T, M] {
      override def decode(msg: M[F], strict: Boolean)(
          implicit M: Message[F, M]): DecodeResult[F, T] =
        DecodeResult(M.body(msg).run >> F.raiseError(t))
      override def consumes: Set[MediaRange] = Set.empty
    }

  implicit def binary[F[_]: Sync, M[_[_]]](
      implicit M: Message[M, F]): EntityDecoder[F, Chunk[Byte], M] =
    EntityDecoder.decodeBy(MediaRange.`*/*`)(collectBinary[F, M])

  implicit def text[F[_]: Sync, M[_[_]]](
      implicit M: Message[F, M],
      defaultCharset: Charset = DefaultCharset): EntityDecoder[F, String, M] =
    EntityDecoder.decodeBy(MediaRange.`text/*`)(msg =>
      collectBinary(msg).map(bs =>
        new String(bs.toArray, M.charset(msg).getOrElse(defaultCharset).nioCharset)))

  // File operations // TODO: rewrite these using NIO non blocking FileChannels, and do these make sense as a 'decoder'?
  def binFile[F[_]: MonadError[?[_], Throwable], M[_[_]]](
      file: File)(implicit F: Sync[F], M: Message[F, M]): EntityDecoder[F, File, M] =
    EntityDecoder.decodeBy(MediaRange.`*/*`) { msg =>
      val sink = writeOutputStream[F](F.delay(new FileOutputStream(file)))
      DecodeResult.success(M.body(msg).to(sink).run).map(_ => file)
    }

  def textFile[F[_]: MonadError[?[_], Throwable], M[_[_]]](
      file: File)(implicit F: Sync[F], M: Message[F, M]): EntityDecoder[F, File, M] =
    EntityDecoder.decodeBy(MediaRange.`text/*`) { msg =>
      val sink = writeOutputStream[F](F.delay(new PrintStream(new FileOutputStream(file))))
      DecodeResult.success(M.body(msg).to(sink).run).map(_ => file)
    }

  implicit def multipart[F[_]: Sync, M[_[_]]](
      implicit M: Message[F, M]): EntityDecoder[F, Multipart[F], M] =
    MultipartDecoder.decoder

  /** An entity decoder that ignores the content and returns unit. */
  implicit def void[F[_]: Sync, M[_[_]]](implicit M: Message[F, M]): EntityDecoder[F, Unit, M] =
    EntityDecoder.decodeBy(MediaRange.`*/*`) { msg =>
      DecodeResult.success(M.body(msg).drain.run)
    }
}
