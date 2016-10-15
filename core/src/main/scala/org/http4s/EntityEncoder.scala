package org.http4s

import java.io.{File, InputStream, Reader}
import java.nio.{ByteBuffer, CharBuffer}
import java.nio.file.Path

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

import org.http4s.EntityEncoder._
import org.http4s.headers.{`Transfer-Encoding`, `Content-Type`}
import org.http4s.multipart.{Multipart, MultipartEncoder}
import scalaz._
import scalaz.concurrent.Task
import scalaz.std.option._
import scalaz.stream.{Process0, Channel, Process, io}
import scalaz.stream.nio.file
import scalaz.stream.Cause.{End, Terminated}
import scalaz.stream.Process.emit
import scalaz.syntax.apply._
import scodec.bits.ByteVector

trait EntityEncoder[A] { self =>

  /** Convert the type `A` to an [[EntityEncoder.Entity]] in the `Task` monad */
  def toEntity(a: A): Task[EntityEncoder.Entity]

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
  final case class Entity(body: EntityBody, length: Option[Long] = None)

  /** summon an implicit [[EntityEncoder]] */
  def apply[A](implicit ev: EntityEncoder[A]): EntityEncoder[A] = ev

  object Entity {
    implicit val entityInstance: Monoid[Entity] = Monoid.instance(
      (a, b) => Entity(a.body ++ b.body, (a.length |@| b.length) { _ + _ }),
      empty
    )

    lazy val empty = Entity(EmptyBody, Some(0L))
  }

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
  def simple[A](hs: Header*)(toChunk: A => ByteVector): EntityEncoder[A] = encodeBy(hs:_*){ a =>
    val c = toChunk(a)
    Task.now(Entity(emit(c), Some(c.length)))
  }
}

trait EntityEncoderInstances0 {
  /** Encodes a value from its Show instance.  Too broad to be implicit, too useful to not exist. */
   def showEncoder[A](implicit charset: Charset = DefaultCharset, show: Show[A]): EntityEncoder[A] = {
    val hdr = `Content-Type`(MediaType.`text/plain`).withCharset(charset)
    simple[A](hdr)(a => ByteVector.view(show.shows(a).getBytes(charset.nioCharset)))
  }

  def emptyEncoder[A]: EntityEncoder[A] = new EntityEncoder[A] {
    def toEntity(a: A): Task[Entity] = Task.now(Entity.empty)
    def headers: Headers = Headers.empty
  }

  implicit def futureEncoder[A](implicit W: EntityEncoder[A], ec: ExecutionContext): EntityEncoder[Future[A]] =
    new EntityEncoder[Future[A]] {
      override def toEntity(a: Future[A]): Task[Entity] = util.task.futureToTask(a).flatMap(W.toEntity)
      override def headers: Headers = W.headers
    }


  implicit def naturalTransformationEncoder[F[_], A](implicit N: ~>[F, Task], W: EntityEncoder[A]): EntityEncoder[F[A]] =
    taskEncoder[A](W).contramap { f: F[A] => N(f) }

  /**
   * A process encoder is intended for streaming, and does not calculate its bodies in
   * advance.  As such, it does not calculate the Content-Length in advance.  This is for
   * use with chunked transfer encoding.
   */
  implicit def sourceEncoder[A](implicit W: EntityEncoder[A]): EntityEncoder[Process[Task, A]] =
    new EntityEncoder[Process[Task, A]] {
      override def toEntity(a: Process[Task, A]): Task[Entity] = {
        Task.now(Entity(a.flatMap(a => Process.await(W.toEntity(a))(_.body)), None))
      }

      override def headers: Headers =
        W.headers.get(`Transfer-Encoding`) match {
          case Some(transferCoding) if transferCoding.hasChunked =>
            W.headers
          case _ =>
            W.headers.put(`Transfer-Encoding`(TransferCoding.chunked))
        }
    }

  implicit def process0Encoder[A](implicit W: EntityEncoder[A]): EntityEncoder[Process0[A]] =
    sourceEncoder[A].contramap(_.toSource)
}

trait EntityEncoderInstances extends EntityEncoderInstances0 {
  private val DefaultChunkSize = 4096

  implicit val unitEncoder: EntityEncoder[Unit] = emptyEncoder[Unit]

  implicit def stringEncoder(implicit charset: Charset = DefaultCharset): EntityEncoder[String] = {
    val hdr = `Content-Type`(MediaType.`text/plain`).withCharset(charset)
    simple(hdr)(s => ByteVector.view(s.getBytes(charset.nioCharset)))
  }

  implicit def charBufferEncoder(implicit charset: Charset = DefaultCharset): EntityEncoder[CharBuffer] =
    stringEncoder.contramap(_.toString)

  implicit def charArrayEncoder(implicit charset: Charset = DefaultCharset): EntityEncoder[Array[Char]] =
    stringEncoder.contramap(new String(_))

  implicit val byteVectorEncoder: EntityEncoder[ByteVector] =
    simple(`Content-Type`(MediaType.`application/octet-stream`))(identity)

  implicit val byteArrayEncoder: EntityEncoder[Array[Byte]] = byteVectorEncoder.contramap(ByteVector.apply)

  implicit val byteBufferEncoder: EntityEncoder[ByteBuffer] = byteVectorEncoder.contramap(ByteVector.apply)

  implicit def taskEncoder[A](implicit W: EntityEncoder[A]): EntityEncoder[Task[A]] = new EntityEncoder[Task[A]] {
    override def toEntity(a: Task[A]): Task[Entity] = a.flatMap(W.toEntity)
    override def headers: Headers = W.headers
  }

    // TODO parameterize chunk size
  // TODO if Header moves to Entity, can add a Content-Disposition with the filename
  implicit val fileEncoder: EntityEncoder[File] =
    chunkedEncoder { f: File => file.chunkR(f.getAbsolutePath) }

  // TODO parameterize chunk size
  // TODO if Header moves to Entity, can add a Content-Disposition with the filename
  implicit val filePathEncoder: EntityEncoder[Path] = fileEncoder.contramap(_.toFile)

  // TODO parameterize chunk size
  implicit def inputStreamEncoder[A <: InputStream]: EntityEncoder[A] =
    chunkedEncoder { is: InputStream => io.chunkR(is) }

  // TODO parameterize chunk size
  implicit def readerEncoder[A <: Reader](implicit charset: Charset = DefaultCharset): EntityEncoder[A] =
    // TODO polish and contribute back to scalaz-stream
    sourceEncoder[Array[Char]].contramap { r: Reader =>
      val unsafeChunkR = io.resource(Task.delay(r))(
        src => Task.delay(src.close())) { src =>
        Task.now { buf: Array[Char] => Task.delay {
          val m = src.read(buf)
          m match {
            case l if l == buf.length => buf
            case -1 => throw Terminated(End)
            case _ => buf.slice(0, m)
          }
        }}
      }
      val chunkR = unsafeChunkR.map(f => (n: Int) => {
        val buf = new Array[Char](n)
        f(buf)
      })
      Process.constant(DefaultChunkSize).toSource.through(chunkR)
    }

  def chunkedEncoder[A](f: A => Channel[Task, Int, ByteVector], chunkSize: Int = DefaultChunkSize): EntityEncoder[A] =
    sourceEncoder[ByteVector].contramap { a => Process.constant(chunkSize).toSource.through(f(a)) }

  implicit val multipartEncoder: EntityEncoder[Multipart] =
    MultipartEncoder

  implicit val entityEncoderContravariant: Contravariant[EntityEncoder] = new Contravariant[EntityEncoder] {
    override def contramap[A, B](r: EntityEncoder[A])(f: (B) => A): EntityEncoder[B] = r.contramap(f)
  }

  implicit val serverSentEventEncoder: EntityEncoder[EventStream] =
    sourceEncoder[ByteVector].contramap[EventStream] { _.pipe(ServerSentEvent.encoder) }
      .withContentType(MediaType.`text/event-stream`)
}
