package org.http4s

import java.io._
import java.nio.CharBuffer
import java.nio.file.Path

import scala.annotation.implicitNotFound
import scala.concurrent.{ExecutionContext, Future}

import cats._
import cats.functor._
import fs2._
import fs2.io._
import fs2.Stream._
import org.http4s.headers._
import org.http4s.multipart._

@implicitNotFound("Cannot convert from ${A} to an Entity, because no EntityEncoder[${A}] instance could be found.")
trait EntityEncoder[A] { self =>
  import EntityEncoder._

  /** Convert the type `A` to an [[EntityEncoder.Entity]] in the `Task` monad */
  def toEntity(a: A): Task[Entity]

  /** Headers that may be added to a [[Message]]
    *
    * Examples of such headers would be Content-Type.
    * __NOTE:__ The Content-Length header will be generated from the resulting Entity and thus should not be added.
    */
  def headers: Headers

  /** Make a new [[EntityEncoder]] using this type as a foundation */
  def contramap[B](f: B => A): EntityEncoder[B] = new EntityEncoder[B] {
    override def toEntity(a: B): Task[Entity] = self.toEntity(f(a))
    override def headers: Headers = self.headers
  }

  /** Get the [[org.http4s.headers.Content-Type]] of the body encoded by this [[EntityEncoder]], if defined the headers */
  def contentType: Option[`Content-Type`] = headers.get(`Content-Type`)

  /** Get the [[Charset]] of the body encoded by this [[EntityEncoder]], if defined the headers */
  def charset: Option[Charset] = headers.get(`Content-Type`).flatMap(_.charset)

  /** Generate a new EntityEncoder that will contain the `Content-Type` header */
  def withContentType(tpe: `Content-Type`): EntityEncoder[A] = new EntityEncoder[A] {
      override def toEntity(a: A): Task[Entity] = self.toEntity(a)
      override val headers: Headers = self.headers.put(tpe)
    }
}

object EntityEncoder extends EntityEncoderInstances {

  /** summon an implicit [[EntityEncoder]] */
  def apply[A](implicit ev: EntityEncoder[A]): EntityEncoder[A] = ev

  /** Create a new [[EntityEncoder]] */
  def encodeBy[A](hs: Headers)(f: A => Task[Entity]): EntityEncoder[A] = new EntityEncoder[A] {
    override def toEntity(a: A): Task[Entity] = f(a)
    override def headers: Headers = hs
  }

  /** Create a new [[EntityEncoder]] */
  def encodeBy[A](hs: Header*)(f: A => Task[Entity]): EntityEncoder[A] = {
    val hdrs = if(hs.nonEmpty) Headers(hs.toList) else Headers.empty
    encodeBy(hdrs)(f)
  }

  /** Create a new [[EntityEncoder]]
    *
    * This constructor is a helper for types that can be serialized synchronously, for example a String.
    */
  def simple[A](hs: Header*)(toChunk: A => Chunk[Byte]): EntityEncoder[A] =
    encodeBy(hs:_*) { a =>
      val c = toChunk(a)
      Task.now(Entity(chunk(c), Some(c.size.toLong)))
    }
}

trait EntityEncoderInstances0 {
  import EntityEncoder._

  /** Encodes a value from its Show instance.  Too broad to be implicit, too useful to not exist. */
   def showEncoder[A](implicit charset: Charset = DefaultCharset, show: Show[A]): EntityEncoder[A] = {
    val hdr = `Content-Type`(MediaType.`text/plain`).withCharset(charset)
     simple[A](hdr)(a => Chunk.bytes(show.show(a).getBytes(charset.nioCharset)))
  }

  def emptyEncoder[A]: EntityEncoder[A] = new EntityEncoder[A] {
    def toEntity(a: A): Task[Entity] = Task.now(Entity.empty)
    def headers: Headers = Headers.empty
  }

  implicit def futureEncoder[A](implicit W: EntityEncoder[A], ec: ExecutionContext): EntityEncoder[Future[A]] =
    new EntityEncoder[Future[A]] {
      implicit val strategy : Strategy = Strategy.fromExecutionContext(ec)
      override def toEntity(a: Future[A]): Task[Entity] = Task.fromFuture(a).flatMap(W.toEntity)
      override def headers: Headers = W.headers
    }


  implicit def naturalTransformationEncoder[F[_], A](implicit N: ~>[F, Task], W: EntityEncoder[A]): EntityEncoder[F[A]] =
    taskEncoder[A](W).contramap { f: F[A] => N(f) }

  /**
   * A process encoder is intended for streaming, and does not calculate its
   * bodies in advance.  As such, it does not calculate the Content-Length in
   * advance.  This is for use with chunked transfer encoding.
   */
  implicit def sourceEncoder[A](implicit W: EntityEncoder[A]): EntityEncoder[Stream[Task, A]] =
    new EntityEncoder[Stream[Task, A]] {
      override def toEntity(a: Stream[Task, A]): Task[Entity] =
        Task.now(Entity(a.evalMap(W.toEntity).flatMap(_.body)))

      override def headers: Headers =
        W.headers.get(`Transfer-Encoding`) match {
          case Some(transferCoding) if transferCoding.hasChunked =>
            W.headers
          case _ =>
            W.headers.put(`Transfer-Encoding`(TransferCoding.chunked))
        }
    }

  implicit def pureStreamEncoder[A](implicit W: EntityEncoder[A]): EntityEncoder[Stream[Nothing, A]] =
    sourceEncoder[A].contramap(_.covary[Task])
}

trait EntityEncoderInstances extends EntityEncoderInstances0 {
  import EntityEncoder._

  private val DefaultChunkSize = 4096

  implicit val unitEncoder: EntityEncoder[Unit] = emptyEncoder[Unit]

  implicit def stringEncoder(implicit charset: Charset = DefaultCharset): EntityEncoder[String] = {
    val hdr = `Content-Type`(MediaType.`text/plain`).withCharset(charset)
    simple(hdr)(s => Chunk.bytes(s.getBytes(charset.nioCharset)))
  }

  implicit def charArrayEncoder(implicit charset: Charset = DefaultCharset): EntityEncoder[Array[Char]] =
    stringEncoder.contramap(new String(_))

  implicit val chunkEncoder: EntityEncoder[Chunk[Byte]] =
    simple(`Content-Type`(MediaType.`application/octet-stream`))(identity)

  implicit val byteArrayEncoder: EntityEncoder[Array[Byte]] =
    chunkEncoder.contramap(Chunk.bytes)

  // TODO fs2 port this is gone in master but is needed by sourceEncoder.
  // That's troubling.  Make this go away.
  implicit val byteEncoder: EntityEncoder[Byte] =
    chunkEncoder.contramap(Chunk.singleton)

  implicit def taskEncoder[A](implicit W: EntityEncoder[A]): EntityEncoder[Task[A]] = new EntityEncoder[Task[A]] {
    override def toEntity(a: Task[A]): Task[Entity] = a.flatMap(W.toEntity)
    override def headers: Headers = W.headers
  }

  // TODO parameterize chunk size
  // TODO if Header moves to Entity, can add a Content-Disposition with the filename
  implicit val fileEncoder: EntityEncoder[File] =
    inputStreamEncoder.contramap(file => Eval.always(new FileInputStream(file)))

  // TODO parameterize chunk size
  // TODO if Header moves to Entity, can add a Content-Disposition with the filename
  implicit val filePathEncoder: EntityEncoder[Path] =
    fileEncoder.contramap(_.toFile)

  // TODO parameterize chunk size
  implicit def inputStreamEncoder[A <: InputStream]: EntityEncoder[Eval[A]] =
    sourceEncoder[Byte].contramap { in: Eval[A] =>
      readInputStream[Task](Task.delay(in.value), DefaultChunkSize)
    }

  // TODO parameterize chunk size
  implicit def readerEncoder[A <: Reader](implicit charset: Charset = DefaultCharset): EntityEncoder[Task[A]] =
    sourceEncoder[Byte].contramap { r: Task[Reader] =>

      // Shared buffer
      val charBuffer = CharBuffer.allocate(DefaultChunkSize)
      val readToBytes: Task[Option[Chunk[Byte]]] = r.map { r =>
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

      def useReader(is: Reader) =
        Stream.eval(readToBytes)
          .repeat
          .through(pipe.unNoneTerminate)
          .flatMap(Stream.chunk)

      // The reader is closed at the end like InputStream
      Stream.bracket(r)(useReader, t => Task.delay(t.close()))
    }

  implicit val multipartEncoder: EntityEncoder[Multipart] =
    MultipartEncoder

  implicit val entityEncoderContravariant: Contravariant[EntityEncoder] = new Contravariant[EntityEncoder] {
    override def contramap[A, B](r: EntityEncoder[A])(f: (B) => A): EntityEncoder[B] = r.contramap(f)
  }

  implicit val serverSentEventEncoder: EntityEncoder[EventStream] =
    sourceEncoder[Byte].contramap[EventStream] { _.through(ServerSentEvent.encoder) }
      .withContentType(MediaType.`text/event-stream`)
}
