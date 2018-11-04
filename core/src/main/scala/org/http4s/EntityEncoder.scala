package org.http4s

import cats._
import cats.effect.{ContextShift, Effect, Sync}
import cats.implicits._
import fs2.Stream._
import fs2._
import fs2.io.file.readAll
import fs2.io.readInputStream
import java.io._
import java.nio.CharBuffer
import java.nio.file.Path
import org.http4s.headers._
import org.http4s.multipart.{Multipart, MultipartEncoder}
import scala.annotation.implicitNotFound
import scala.concurrent.{ExecutionContext, blocking}

@implicitNotFound(
  "Cannot convert from ${A} to an Entity, because no EntityEncoder[${F}, ${A}] instance could be found.")
trait EntityEncoder[F[_], A] { self =>

  /** Convert the type `A` to an [[EntityEncoder.Entity]] in the effect type `F` */
  def toEntity(a: A): Entity[F]

  /** Headers that may be added to a [[Message]]
    *
    * Examples of such headers would be Content-Type.
    * __NOTE:__ The Content-Length header will be generated from the resulting Entity and thus should not be added.
    */
  def headers: Headers

  /** Make a new [[EntityEncoder]] using this type as a foundation */
  def contramap[B](f: B => A): EntityEncoder[F, B] = new EntityEncoder[F, B] {
    override def toEntity(a: B): Entity[F] = self.toEntity(f(a))
    override def headers: Headers = self.headers
  }

  /** Get the [[org.http4s.headers.Content-Type]] of the body encoded by this [[EntityEncoder]], if defined the headers */
  def contentType: Option[`Content-Type`] = headers.get(`Content-Type`)

  /** Get the [[Charset]] of the body encoded by this [[EntityEncoder]], if defined the headers */
  def charset: Option[Charset] = headers.get(`Content-Type`).flatMap(_.charset)

  /** Generate a new EntityEncoder that will contain the `Content-Type` header */
  def withContentType(tpe: `Content-Type`): EntityEncoder[F, A] = new EntityEncoder[F, A] {
    override def toEntity(a: A): Entity[F] = self.toEntity(a)
    override val headers: Headers = self.headers.put(tpe)
  }
}

object EntityEncoder {

  private val DefaultChunkSize = 4096

  /** summon an implicit [[EntityEncoder]] */
  def apply[F[_], A](implicit ev: EntityEncoder[F, A]): EntityEncoder[F, A] = ev

  /** Create a new [[EntityEncoder]] */
  def encodeBy[F[_], A](hs: Headers)(f: A => Entity[F]): EntityEncoder[F, A] =
    new EntityEncoder[F, A] {
      override def toEntity(a: A): Entity[F] = f(a)
      override def headers: Headers = hs
    }

  /** Create a new [[EntityEncoder]] */
  def encodeBy[F[_], A](hs: Header*)(f: A => Entity[F]): EntityEncoder[F, A] = {
    val hdrs = if (hs.nonEmpty) Headers(hs.toList) else Headers.empty
    encodeBy(hdrs)(f)
  }

  /** Create a new [[EntityEncoder]]
    *
    * This constructor is a helper for types that can be serialized synchronously, for example a String.
    */
  def simple[F[_], A](hs: Header*)(toChunk: A => Chunk[Byte]): EntityEncoder[F, A] =
    encodeBy(hs: _*) { a =>
      val c = toChunk(a)
      Entity[F](chunk(c).covary[F], Some(c.size.toLong))
    }

  /** Encodes a value from its Show instance.  Too broad to be implicit, too useful to not exist. */
  def showEncoder[F[_], A](
      implicit charset: Charset = DefaultCharset,
      show: Show[A]): EntityEncoder[F, A] = {
    val hdr = `Content-Type`(MediaType.text.plain).withCharset(charset)
    simple[F, A](hdr)(a => Chunk.bytes(show.show(a).getBytes(charset.nioCharset)))
  }

  def emptyEncoder[F[_], A]: EntityEncoder[F, A] =
    new EntityEncoder[F, A] {
      def toEntity(a: A): Entity[F] = Entity.empty
      def headers: Headers = Headers.empty
    }

  /**
    * A stream encoder is intended for streaming, and does not calculate its
    * bodies in advance.  As such, it does not calculate the Content-Length in
    * advance.  This is for use with chunked transfer encoding.
    */
  implicit def streamEncoder[F[_], A](
      implicit W: EntityEncoder[F, A]): EntityEncoder[F, Stream[F, A]] =
    new EntityEncoder[F, Stream[F, A]] {
      override def toEntity(a: Stream[F, A]): Entity[F] =
        Entity(a.flatMap(W.toEntity(_).body))

      override def headers: Headers =
        W.headers.get(`Transfer-Encoding`) match {
          case Some(transferCoding) if transferCoding.hasChunked =>
            W.headers
          case _ =>
            W.headers.put(`Transfer-Encoding`(TransferCoding.chunked))
        }
    }

  implicit def unitEncoder[F[_]]: EntityEncoder[F, Unit] =
    emptyEncoder[F, Unit]

  implicit def stringEncoder[F[_]](
      implicit charset: Charset = DefaultCharset): EntityEncoder[F, String] = {
    val hdr = `Content-Type`(MediaType.text.plain).withCharset(charset)
    simple(hdr)(s => Chunk.bytes(s.getBytes(charset.nioCharset)))
  }

  implicit def charArrayEncoder[F[_]](
      implicit charset: Charset = DefaultCharset): EntityEncoder[F, Array[Char]] =
    stringEncoder[F].contramap(new String(_))

  implicit def chunkEncoder[F[_]]: EntityEncoder[F, Chunk[Byte]] =
    simple(`Content-Type`(MediaType.application.`octet-stream`))(identity)

  implicit def byteArrayEncoder[F[_]]: EntityEncoder[F, Array[Byte]] =
    chunkEncoder[F].contramap(Chunk.bytes)

  /** Encodes an entity body.  Chunking of the stream is preserved.  A
    * `Transfer-Encoding: chunked` header is set, as we cannot know
    * the content length without running the stream.
    */
  implicit def entityBodyEncoder[F[_]]: EntityEncoder[F, EntityBody[F]] =
    encodeBy(`Transfer-Encoding`(TransferCoding.chunked)) { body =>
      Entity(body, None)
    }

  // TODO parameterize chunk size
  // TODO if Header moves to Entity, can add a Content-Disposition with the filename
  def fileEncoder[F[_]](blockingExecutionContext: ExecutionContext)(
      implicit F: Effect[F],
      cs: ContextShift[F]): EntityEncoder[F, File] =
    filePathEncoder[F](blockingExecutionContext).contramap(_.toPath)

  // TODO parameterize chunk size
  // TODO if Header moves to Entity, can add a Content-Disposition with the filename
  def filePathEncoder[F[_]: Sync: ContextShift](
      blockingExecutionContext: ExecutionContext): EntityEncoder[F, Path] =
    encodeBy[F, Path](`Transfer-Encoding`(TransferCoding.chunked)) { p =>
      Entity(readAll[F](p, blockingExecutionContext, 4096)) //2 KB :P
    }

  // TODO parameterize chunk size
  def inputStreamEncoder[F[_]: Sync: ContextShift, IS <: InputStream](
      blockingExecutionContext: ExecutionContext): EntityEncoder[F, F[IS]] =
    entityBodyEncoder[F].contramap { in: F[IS] =>
      readInputStream[F](in.widen[InputStream], DefaultChunkSize, blockingExecutionContext)
    }

  // TODO parameterize chunk size
  implicit def readerEncoder[F[_], R <: Reader](blockingExecutionContext: ExecutionContext)(
      implicit F: Sync[F],
      cs: ContextShift[F],
      charset: Charset = DefaultCharset): EntityEncoder[F, F[R]] =
    entityBodyEncoder[F].contramap { fr: F[R] =>
      // Shared buffer
      val charBuffer = CharBuffer.allocate(DefaultChunkSize)
      def readToBytes(r: Reader): F[Option[Chunk[Byte]]] =
        for {
          // Read into the buffer
          readChars <- cs.evalOn(blockingExecutionContext)(F.delay(blocking(r.read(charBuffer))))
        } yield {
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
            Some(Chunk.bytes(b))
          }
        }

      def useReader(r: Reader) =
        Stream
          .eval(readToBytes(r))
          .repeat
          .unNoneTerminate
          .flatMap(Stream.chunk[F, Byte])

      // The reader is closed at the end like InputStream
      Stream.bracket(fr)(r => F.delay(r.close())).flatMap(useReader)
    }

  implicit def multipartEncoder[F[_]]: EntityEncoder[F, Multipart[F]] =
    new MultipartEncoder[F]

  implicit def entityEncoderContravariant[F[_]]: Contravariant[EntityEncoder[F, ?]] =
    new Contravariant[EntityEncoder[F, ?]] {
      override def contramap[A, B](r: EntityEncoder[F, A])(f: (B) => A): EntityEncoder[F, B] =
        r.contramap(f)
    }

  implicit def serverSentEventEncoder[F[_]]: EntityEncoder[F, EventStream[F]] =
    entityBodyEncoder[F]
      .contramap[EventStream[F]] { _.through(ServerSentEvent.encoder) }
      .withContentType(`Content-Type`(MediaType.`text/event-stream`))
}
