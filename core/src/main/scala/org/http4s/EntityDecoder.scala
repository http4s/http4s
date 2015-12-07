package org.http4s

import java.io.{File, FileOutputStream, StringReader}
import javax.xml.parsers.SAXParser

import org.xml.sax.{InputSource, SAXParseException}
import java.io.{File, FileOutputStream}
import org.http4s.headers.`Content-Type`
import scodec.bits.ByteVector

import scala.annotation.unchecked.uncheckedVariance
import scala.util.control.NonFatal
import scalaz.Liskov.{<~<, refl}
import scalaz.concurrent.Task
import scalaz.std.string._
import scalaz.stream.{io, process1}
import scalaz.syntax.monad._
import scalaz.{-\/, EitherT, \/, \/-}

import util.UrlFormCodec.{ decode => formDecode }
import util.byteVector._

/** A type that can be used to decode a [[Message]]
  * EntityDecoder is used to attempt to decode a [[Message]] returning the
  * entire resulting A. If an error occurs it will result in a failed Task
  * The default decoders provided here are not streaming, but one could implement
  * a streaming decoder by having the value of A be some kind of streaming construct.
  * @tparam T result type produced by the decoder
  */
trait EntityDecoder[T] { self =>
  /** Attempt to decode the body of the [[Message]] */
  def decode(msg: Message, strict: Boolean): DecodeResult[T]

  /** The [[MediaRange]]s this [[EntityDecoder]] knows how to handle */
  def consumes: Set[MediaRange]

  /** Make a new [[EntityDecoder]] by mapping the output result */
  def map[T2](f: T => T2): EntityDecoder[T2] = new EntityDecoder[T2] {
    override def consumes: Set[MediaRange] = self.consumes

    override def decode(msg: Message, strict: Boolean): DecodeResult[T2] = self.decode(msg, strict).map(f)
  }

  def flatMapR[T2](f: T => DecodeResult[T2]): EntityDecoder[T2] = new EntityDecoder[T2] {
    override def decode(msg: Message, strict: Boolean): DecodeResult[T2] = self.decode(msg, strict).flatMap(f)

    override def consumes: Set[MediaRange] = self.consumes
  }

  /** Combine two [[EntityDecoder]]'s
    *
    * The new [[EntityDecoder]] will first attempt to determine if it can perform the decode,
    * and if not, defer to the second [[EntityDecoder]]
    * @param other backup [[EntityDecoder]]
    */
  def orElse[T2](other: EntityDecoder[T2])(implicit ev: T <~< T2): EntityDecoder[T2] =
    new EntityDecoder.OrDec(widen[T2], other)

  /** true if this [[EntityDecoder]] knows how to decode the provided [[MediaType]] */
  def matchesMediaType(mediaType: MediaType): Boolean =
    consumes.exists(_.satisfiedBy(mediaType))

  // shamelessly stolen from IList
  def widen[B](implicit ev: T <~< B): EntityDecoder[B] =
    ev.subst[({type λ[-α] = EntityDecoder[α @uncheckedVariance] <~< EntityDecoder[B]})#λ](refl)(this)
}

/** EntityDecoder is used to attempt to decode an [[EntityBody]]
  * This companion object provides a way to create `new EntityDecoder`s along
  * with some commonly used instances which can be resolved implicitly.
  */
object EntityDecoder extends EntityDecoderInstances {

  // This is not a real media type but will still be matched by `*/*`
  private val UndefinedMediaType = new MediaType("UNKNOWN","UNKNOWN")

  /** summon an implicit [[EntityEncoder]] */
  def apply[T](implicit ev: EntityDecoder[T]): EntityDecoder[T] = ev

  /** Create a new [[EntityDecoder]]
    *
    * The new [[EntityDecoder]] will attempt to decode messages of type `T`
    * only if the [[Message]] satisfies the provided [[MediaRange]]s
    */
  def decodeBy[T](r1: MediaRange, rs: MediaRange*)(f: Message => DecodeResult[T]): EntityDecoder[T] = new EntityDecoder[T] {
    override def decode(msg: Message, strict: Boolean): DecodeResult[T] = {
      try {
        if (strict) msg.headers.get(`Content-Type`) match {
          case Some(c) if matchesMediaType(c.mediaType) => f(msg)
          case Some(c) => DecodeResult.failure(MediaTypeMismatch(c.mediaType, consumes))
          case None if matchesMediaType(UndefinedMediaType) => f(msg)
          case None => DecodeResult.failure(MediaTypeMissing(consumes))
        }
        else f(msg)
      }
      catch {
        case NonFatal(e) => DecodeResult[T](Task.fail(e))
      }
    }

    override val consumes: Set[MediaRange] = (r1 +: rs).toSet
  }

  private class OrDec[T](a: EntityDecoder[T], b: EntityDecoder[T]) extends EntityDecoder[T] {
    override def decode(msg: Message, strict: Boolean): DecodeResult[T] = {
      msg.headers.get(`Content-Type`) match {
        case Some(contentType) =>
          if (a.matchesMediaType(contentType.mediaType)) a.decode(msg, strict)
          else b.decode(msg, strict)

        case None =>
          if (a.matchesMediaType(UndefinedMediaType)) a.decode(msg, strict)
          else b.decode(msg, strict)
      }
    }

    override val consumes: Set[MediaRange] = a.consumes ++ b.consumes
  }

  /** Helper method which simply gathers the body into a single ByteVector */
  def collectBinary(msg: Message): DecodeResult[ByteVector] =
    DecodeResult.success(msg.body.runFoldMap(identity))

  /** Decodes a message to a String */
  def decodeString(msg: Message)(implicit defaultCharset: Charset = DefaultCharset): Task[String] =
    msg.bodyAsText.foldMonoid.runLastOr("")
}

/** Implementations of the EntityDecoder instances */
trait EntityDecoderInstances {
  import org.http4s.EntityDecoder._

  /////////////////// Instances //////////////////////////////////////////////

  /** Provides a mechanism to fail decoding */
  def error[T](t: Throwable) = new EntityDecoder[T] {
    override def decode(msg: Message, strict: Boolean): DecodeResult[T] = {
      DecodeResult(msg.body.kill.run.flatMap(_ => Task.fail(t)))
    }
    override def consumes: Set[MediaRange] = Set.empty
  }

  implicit val binary: EntityDecoder[ByteVector] = {
    EntityDecoder.decodeBy(MediaRange.`*/*`)(collectBinary)
  }

  implicit def text(implicit defaultCharset: Charset = DefaultCharset): EntityDecoder[String] =
    EntityDecoder.decodeBy(MediaRange.`text/*`)(msg =>
      collectBinary(msg).map(bs => new String(bs.toArray, msg.charset.getOrElse(defaultCharset).nioCharset))
    )

  // File operations // TODO: rewrite these using NIO non blocking FileChannels, and do these make sense as a 'decoder'?
  def binFile(file: File): EntityDecoder[File] =
    EntityDecoder.decodeBy(MediaRange.`*/*`){ msg =>
      val p = io.chunkW(new java.io.FileOutputStream(file))
      DecodeResult.success(msg.body.to(p).run).map(_ => file)
    }

  def textFile(file: java.io.File): EntityDecoder[File] =
    EntityDecoder.decodeBy(MediaRange.`text/*`){ msg =>
      val p = io.chunkW(new java.io.PrintStream(new FileOutputStream(file)))
      DecodeResult.success(msg.body.to(p).run).map(_ => file)
    }
}

object DecodeResult {
  def apply[A](task: Task[ParseResult[A]]): DecodeResult[A] = EitherT[Task, DecodeFailure, A](task)

  def success[A](a: Task[A]): DecodeResult[A] = EitherT.right(a)

  def success[A](a: A): DecodeResult[A] = EitherT(Task.now(\/-(a): ParseFailure\/A))

  def failure[A](e: Task[DecodeFailure]): DecodeResult[A] = EitherT.left(e)

  def failure[A](e: DecodeFailure): DecodeResult[A] = EitherT(Task.now(-\/(e): DecodeFailure\/A))
}
