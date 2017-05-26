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
import org.http4s.syntax.async._

import scala.annotation.implicitNotFound
import scala.concurrent.{ExecutionContext, Future}

@implicitNotFound("Cannot convert from ${A} to an Entity, because no EntityEncoder[${F}, ${A}] instance could be found.")
trait EntityEncoder[F[_], A] { self =>

  /** Convert the type `A` to an [[EntityEncoder.Entity]] in the effect type `F` */
  def toEntity(a: A): F[Entity[F]]

  /** Headers that may be added to a [[Message]]
    *
    * Examples of such headers would be Content-Type.
    * __NOTE:__ The Content-Length header will be generated from the resulting Entity and thus should not be added.
    */
  def headers: Headers

  /** Make a new [[EntityEncoder]] using this type as a foundation */
  def contramap[B](f: B => A): EntityEncoder[F, B] = new EntityEncoder[F, B] {
    override def toEntity(a: B): F[Entity[F]] = self.toEntity(f(a))
    override def headers: Headers = self.headers
  }

  /** Get the [[org.http4s.headers.Content-Type]] of the body encoded by this [[EntityEncoder]], if defined the headers */
  def contentType: Option[`Content-Type`] = headers.get(`Content-Type`)

  /** Get the [[Charset]] of the body encoded by this [[EntityEncoder]], if defined the headers */
  def charset: Option[Charset] = headers.get(`Content-Type`).flatMap(_.charset)

  /** Generate a new EntityEncoder that will contain the `Content-Type` header */
  def withContentType(tpe: `Content-Type`): EntityEncoder[F, A] = new EntityEncoder[F, A] {
      override def toEntity(a: A): F[Entity[F]] = self.toEntity(a)
      override val headers: Headers = self.headers.put(tpe)
    }
}

object EntityEncoder extends EntityEncoderInstances {

  /** summon an implicit [[EntityEncoder]] */
  def apply[F[_], A](implicit ev: EntityEncoder[F, A]): EntityEncoder[F, A] = ev

  /** Create a new [[EntityEncoder]] */
  def encodeBy[F[_], A](hs: Headers)(f: A => F[Entity[F]]): EntityEncoder[F, A] =
    new EntityEncoder[F, A] {
      override def toEntity(a: A): F[Entity[F]] = f(a)
      override def headers: Headers = hs
    }

  /** Create a new [[EntityEncoder]] */
  def encodeBy[F[_], A](hs: Header*)(f: A => F[Entity[F]]): EntityEncoder[F, A] = {
    val hdrs = if(hs.nonEmpty) Headers(hs.toList) else Headers.empty
    encodeBy(hdrs)(f)
  }

  /** Create a new [[EntityEncoder]]
    *
    * This constructor is a helper for types that can be serialized synchronously, for example a String.
    */
  def simple[F[_], A](hs: Header*)(toChunk: A => Chunk[Byte])(implicit F: Applicative[F]): EntityEncoder[F, A] =
    encodeBy(hs:_*) { a =>
      val c = toChunk(a)
      F.pure(Entity(chunk(c), Some(c.size.toLong)))
    }
}

trait EntityEncoderInstances0 {
  import EntityEncoder._

  /** Encodes a value from its Show instance.  Too broad to be implicit, too useful to not exist. */
   def showEncoder[F[_]: Applicative, A](implicit charset: Charset = DefaultCharset, show: Show[A]): EntityEncoder[F, A] = {
     val hdr = `Content-Type`(MediaType.`text/plain`).withCharset(charset)
     simple[F, A](hdr)(a => Chunk.bytes(show.show(a).getBytes(charset.nioCharset)))
  }

  def emptyEncoder[F[_], A](implicit F: Applicative[F]): EntityEncoder[F, A] = new EntityEncoder[F, A] {
    def toEntity(a: A): F[Entity[F]] = F.pure(Entity.empty)
    def headers: Headers = Headers.empty
  }

  implicit def futureEncoder[F[_], A](implicit F: Async[F], ec: ExecutionContext, W: EntityEncoder[F, A]): EntityEncoder[F, Future[A]] =
    new EntityEncoder[F, Future[A]] {
      override def toEntity(future: Future[A]): F[Entity[F]] =
        F.fromFuture(future).flatMap(W.toEntity)

      override def headers: Headers = Headers.empty
    }

  /**
   * A stream encoder is intended for streaming, and does not calculate its
   * bodies in advance.  As such, it does not calculate the Content-Length in
   * advance.  This is for use with chunked transfer encoding.
   */
  implicit def streamEncoder[F[_], A](implicit F: Applicative[F], W: EntityEncoder[F, A]): EntityEncoder[F, Stream[F, A]] =
    new EntityEncoder[F, Stream[F, A]] {
      override def toEntity(a: Stream[F, A]): F[Entity[F]] =
        F.pure(Entity(a.evalMap(W.toEntity).flatMap(_.body)))

      override def headers: Headers =
        W.headers.get(`Transfer-Encoding`) match {
          case Some(transferCoding) if transferCoding.hasChunked =>
            W.headers
          case _ =>
            W.headers.put(`Transfer-Encoding`(TransferCoding.chunked))
        }
    }
}

trait EntityEncoderInstances extends EntityEncoderInstances0 {
  import EntityEncoder._

  private val DefaultChunkSize = 4096

  implicit def unitEncoder[F[_]: Applicative]: EntityEncoder[F, Unit] =
    emptyEncoder[F, Unit]

  implicit def stringEncoder[F[_]](implicit F: Applicative[F], charset: Charset = DefaultCharset): EntityEncoder[F, String] = {
    val hdr = `Content-Type`(MediaType.`text/plain`).withCharset(charset)
    simple(hdr)(s => Chunk.bytes(s.getBytes(charset.nioCharset)))
  }

  implicit def charArrayEncoder[F[_]](implicit F: Applicative[F], charset: Charset = DefaultCharset): EntityEncoder[F, Array[Char]] =
    stringEncoder[F].contramap(new String(_))

  implicit def chunkEncoder[F[_]: Applicative]: EntityEncoder[F, Chunk[Byte]] =
    simple(`Content-Type`(MediaType.`application/octet-stream`))(identity)

  implicit def byteArrayEncoder[F[_]: Applicative]: EntityEncoder[F, Array[Byte]] =
    chunkEncoder[F].contramap(Chunk.bytes)

  // TODO fs2 port this is gone in master but is needed by sourceEncoder.
  // That's troubling.  Make this go away.
  implicit def byteEncoder[F[_]: Applicative]: EntityEncoder[F, Byte] =
    chunkEncoder[F].contramap(Chunk.singleton)

  implicit def effectEncoder[F[_], A](implicit F: FlatMap[F], W: EntityEncoder[F, A]): EntityEncoder[F, F[A]] =
    new EntityEncoder[F, F[A]] {
      override def toEntity(a: F[A]): F[Entity[F]] = a.flatMap(W.toEntity)
      override def headers: Headers = W.headers
    }

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
    streamEncoder[F, Byte].contramap { in: F[IS] =>
      readInputStream[F](in.widen[InputStream], DefaultChunkSize)
    }

  // TODO parameterize chunk size
  implicit def readerEncoder[F[_], R <: Reader](implicit F: Sync[F], charset: Charset = DefaultCharset): EntityEncoder[F, F[R]] =
    streamEncoder[F, Byte].contramap { r: F[R] =>
      // Shared buffer
      val charBuffer = CharBuffer.allocate(DefaultChunkSize)
      val readToBytes: F[Option[Chunk[Byte]]] = r.map { r =>
        // Read into the buffer
        val readChars = r.read(charBuffer)

        // Flip to read
        charBuffer.flip()

        if (readChars < 0) None
        else if (readChars == 0) Some(Chunk.empty)
        else {
          // Encode to bytes according to the charset
          val bb = charset.nioCharset.encode(charBuffer)
          // Read into a Chunk
          val b = new Array[Byte](bb.remaining())
          bb.get(b)
          Some(Chunk.bytes(b, 0, b.length))
        }
      }

      def useReader(is: R) =
        Stream.eval(readToBytes)
          .repeat
          .through(pipe.unNoneTerminate)
          .flatMap(Stream.chunk)

      // The reader is closed at the end like InputStream
      Stream.bracket(r)(useReader, t => F.delay(t.close()))
    }

  // TODO fs2 port
  /*
  implicit val multipartEncoder: EntityEncoder[Multipart] =
    MultipartEncoder
   */

  implicit def entityEncoderContravariant[F[_]]: Contravariant[EntityEncoder[F, ?]] =
    new Contravariant[EntityEncoder[F, ?]] {
      override def contramap[A, B](r: EntityEncoder[F, A])(f: (B) => A): EntityEncoder[F, B] =
        r.contramap(f)
    }

  implicit def serverSentEventEncoder[F[_]: Applicative]: EntityEncoder[F, EventStream[F]] =
    streamEncoder[F, Byte].contramap[EventStream[F]] { _.through(ServerSentEvent.encoder) }
      .withContentType(MediaType.`text/event-stream`)
}
