package org.http4s

import java.io.{File, InputStream, Reader}
import java.nio.ByteBuffer
import java.nio.file.Path

import scala.language.implicitConversions

import org.http4s.EntityEncoder._
import org.http4s.Header.`Content-Type`
import scalaz._
import scalaz.concurrent.Task
import scalaz.std.option._
import scalaz.stream.{Process0, Channel, Process, io}
import scalaz.stream.Cause.{End, Terminated}
import scalaz.stream.Process.emit
import scalaz.syntax.apply._
import scodec.bits.ByteVector

case class EntityEncoder[A](
  toEntity: A => Task[EntityEncoder.Entity],
  headers: Headers
) {
  def contramap[B](f: B => A): EntityEncoder[B] = copy(toEntity = f andThen toEntity)

  def contentType: Option[MediaType] = headers.get(`Content-Type`).map(_.mediaType)

  def charset: Option[Charset] = headers.get(`Content-Type`).map(_.charset)

  def withContentType(contentType: `Content-Type`): EntityEncoder[A] =
    copy(headers = headers.put(contentType))
}

object EntityEncoder extends EntityEncoderInstances {
  case class Entity(body: EntityBody, length: Option[Int] = None)

  /** summon an implicit [[EntityEncoder]] */
  def apply[A](implicit ev: EntityEncoder[A]): EntityEncoder[A] = ev

  object Entity {
    implicit val entityInstance: Monoid[Entity] = Monoid.instance(
      (a, b) => Entity(a.body ++ b.body, (a.length |@| b.length) { _ + _ }),
      empty
    )

    lazy val empty = Entity(EmptyBody, Some(0))
  }

  def simple[A](toChunk: A => ByteVector, headers: Headers = Headers.empty): EntityEncoder[A] = EntityEncoder(
    toChunk andThen { chunk => Task.now(Entity(emit(chunk), Some(chunk.size))) },
    headers
  )
}

trait EntityEncoderInstances0 {
  implicit def showEncoder[A](implicit charset: Charset = Charset.`UTF-8`, show: Show[A]): EntityEncoder[A] =
    simple(
      a => ByteVector.view(show.shows(a).getBytes(charset.nioCharset)),
      Headers(`Content-Type`(MediaType.`text/plain`).withCharset(charset))
    )

  implicit def naturalTransformationEncoder[F[_], A](implicit N: ~>[F, Task], W: EntityEncoder[A]): EntityEncoder[F[A]] =
    taskEncoder[A](W).contramap { f: F[A] => N(f) }

  /**
   * A process encoder is intended for streaming, and does not calculate its bodies in
   * advance.  As such, it does not calculate the Content-Length in advance.  This is for
   * use with chunked transfer encoding.
   */
  implicit def sourceEncoder[A](implicit W: EntityEncoder[A]): EntityEncoder[Process[Task, A]] =
    W.copy(toEntity = { process =>
      Task.now(Entity(process.flatMap(a => Process.await(W.toEntity(a))(_.body)), None))
    })

  implicit def process0Encoder[A](implicit W: EntityEncoder[A]): EntityEncoder[Process0[A]] =
    sourceEncoder[A].contramap(_.toSource)
}

trait EntityEncoderInstances extends EntityEncoderInstances0 {
  implicit def stringEncoder(implicit charset: Charset = Charset.`UTF-8`): EntityEncoder[String] = simple(
    s => ByteVector.view(s.getBytes(charset.nioCharset)),
    Headers(`Content-Type`(MediaType.`text/plain`).withCharset(charset))
  )

  implicit def charSequenceEncoder[A <: CharSequence](implicit charset: Charset = Charset.`UTF-8`): EntityEncoder[CharSequence] =
    stringEncoder.contramap(_.toString)

  implicit def charArrayEncoder(implicit charset: Charset = Charset.`UTF-8`): EntityEncoder[Array[Char]] =
    charSequenceEncoder.contramap(new String(_))

  implicit val byteVectorEncoder: EntityEncoder[ByteVector] = simple(
    identity,
    Headers(`Content-Type`(MediaType.`application/octet-stream`))
  )

  implicit val byteArrayEncoder: EntityEncoder[Array[Byte]] = byteVectorEncoder.contramap(ByteVector.apply)

  implicit val byteBufferEncoder: EntityEncoder[ByteBuffer] = byteVectorEncoder.contramap(ByteVector.apply)

  // TODO split off to module to drop scala-xml core dependency
  // TODO infer HTML, XHTML, etc.
  implicit def htmlEncoder(implicit charset: Charset = Charset.`UTF-8`): EntityEncoder[xml.Elem] = simple(
    xml => ByteVector.view(xml.buildString(false).getBytes(charset.nioCharset)),
    Headers(`Content-Type`(MediaType.`text/html`).withCharset(charset))
  )

  implicit def taskEncoder[A](implicit W: EntityEncoder[A]): EntityEncoder[Task[A]] =
    W.copy(toEntity = _.flatMap(W.toEntity))

  // TODO parameterize chunk size
  // TODO if Header moves to Entity, can add a Content-Disposition with the filename
  implicit val fileEncoder: EntityEncoder[File] =
    chunkedEncoder { f: File => io.fileChunkR(f.getAbsolutePath) }

  // TODO parameterize chunk size
  // TODO if Header moves to Entity, can add a Content-Disposition with the filename
  implicit val filePathEncoder: EntityEncoder[Path] = fileEncoder.contramap(_.toFile)

  // TODO parameterize chunk size
  implicit def inputStreamEncoder[A <: InputStream]: EntityEncoder[A] =
    chunkedEncoder { is: InputStream => io.chunkR(is) }

  // TODO parameterize chunk size
  implicit def readerEncoder[A <: Reader](implicit charset: Charset = Charset.`UTF-8`): EntityEncoder[A] =
    // TODO polish and contribute back to scalaz-stream
    sourceEncoder[Array[Char]].contramap { r: Reader =>
      val unsafeChunkR = io.resource(Task.delay(r))(
        src => Task.delay(src.close())) { src =>
        Task.now { buf: Array[Char] => Task.delay {
          val m = src.read(buf)
          println("BUFFER = "+buf.subSequence(0, m))
          if (m == buf.length) buf
          else if (m == -1) throw Terminated(End)
          else buf.slice(0, m)
        }}
      }
      val chunkR = unsafeChunkR.map(f => (n: Int) => {
        val buf = new Array[Char](n)
        f(buf)
      })
      Process.constant(4096).toSource.through(chunkR)
    }

  def chunkedEncoder[A](f: A => Channel[Task, Int, ByteVector], chunkSize: Int = 4096): EntityEncoder[A] =
    sourceEncoder[ByteVector].contramap { a => Process.constant(chunkSize).toSource.through(f(a)) }

  implicit val entityEncoderContravariant: Contravariant[EntityEncoder] = new Contravariant[EntityEncoder] {
    override def contramap[A, B](r: EntityEncoder[A])(f: (B) => A): EntityEncoder[B] = r.contramap(f)
  }
}
