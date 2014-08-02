package org.http4s

import java.io.{InputStream, File, Reader}
import java.nio.ByteBuffer
import java.nio.file.Path
import scala.language.implicitConversions
import scalaz._
import scalaz.concurrent.Task
import scalaz.std.option._
import scalaz.stream.{Channel, io, Process}
import scalaz.stream.Process.{End, emit}
import scalaz.syntax.apply._
import scodec.bits.ByteVector

import org.http4s.Header.`Content-Type`

case class Writable[-A](
  toEntity: A => Task[Writable.Entity],
  headers: Headers
) {
  def contramap[B](f: B => A): Writable[B] = copy(toEntity = f andThen toEntity)

  def withContentType(contentType: `Content-Type`): Writable[A] =
    copy(headers = headers.put(contentType))
}

object Writable extends WritableInstances {
  case class Entity(body: EntityBody, length: Option[Int] = None)

  object Entity {
    implicit val entityInstance: Monoid[Entity] = Monoid.instance(
      (a, b) => Entity(a.body ++ b.body, (a.length |@| b.length) { _ + _ }),
      empty
    )

    lazy val empty = Entity(EmptyBody, Some(0))
  }

  def simple[A](toChunk: A => ByteVector, headers: Headers = Headers.empty): Writable[A] = Writable(
    toChunk andThen { chunk => Task.now(Entity(emit(chunk), Some(chunk.size))) },
    headers
  )
}

import Writable._

trait WritableInstances0 {
  implicit def showWritable[A](implicit charset: Charset = Charset.`UTF-8`, show: Show[A]): Writable[A] =
    simple(
      a => ByteVector.view(show.shows(a).getBytes(charset.nioCharset)),
      Headers(`Content-Type`.`text/plain`.withCharset(charset))
    )

  implicit def naturalTransformationWritable[F[_], A](implicit N: ~>[F, Task], W: Writable[A]): Writable[F[A]] =
    taskWritable[A](W).contramap { f: F[A] => N(f) }

  /**
   * A process writable is intended for streaming, and does not calculate its bodies in
   * advance.  As such, it does not calculate the Content-Length in advance.  This is for
   * use with chunked transfer encoding.
   */
  implicit def processWritable[A](implicit W: Writable[A]): Writable[Process[Task, A]] =
    W.copy(toEntity = { process =>
      Task.now(Entity(process.flatMap(a => Process.await(W.toEntity(a))(_.body)), None))
    })
}

trait WritableInstances extends WritableInstances0 {
  implicit def stringWritable(implicit charset: Charset = Charset.`UTF-8`): Writable[String] = simple(
    s => ByteVector.view(s.getBytes(charset.nioCharset)),
    Headers(`Content-Type`.`text/plain`.withCharset(charset))
  )

  implicit def charSequenceWritable(implicit charset: Charset = Charset.`UTF-8`): Writable[CharSequence] =
    stringWritable.contramap(_.toString)

  implicit def charArrayWritable(implicit charset: Charset = Charset.`UTF-8`): Writable[Array[Char]] =
    stringWritable.contramap(new String(_))

  implicit val byteVectorWritable: Writable[ByteVector] = simple(
    identity,
    Headers(`Content-Type`.`application/octet-stream`)
  )

  implicit val byteArrayWritable: Writable[Array[Byte]] = byteVectorWritable.contramap(ByteVector.apply)

  implicit val byteBufferWritable: Writable[ByteBuffer] = byteVectorWritable.contramap(ByteVector.apply)

  // TODO split off to module to drop scala-xml core dependency
  // TODO infer HTML, XHTML, etc.
  implicit def htmlWritable(implicit charset: Charset = Charset.`UTF-8`): Writable[xml.Elem] = simple(
    xml => ByteVector.view(xml.buildString(false).getBytes(charset.nioCharset)),
    Headers(`Content-Type`(MediaType.`text/html`).withCharset(charset))
  )

  implicit def taskWritable[A](implicit W: Writable[A]): Writable[Task[A]] =
    W.copy(toEntity = _.flatMap(W.toEntity))

  // TODO parameterize chunk size
  // TODO if Header moves to Entity, can add a Content-Disposition with the filename
  implicit val fileWritable: Writable[File] =
    chunkedWritable { f: File => io.fileChunkR(f.getAbsolutePath) }

  // TODO parameterize chunk size
  // TODO if Header moves to Entity, can add a Content-Disposition with the filename
  implicit val filePathWritable: Writable[Path] = fileWritable.contramap(_.toFile)

  // TODO parameterize chunk size
  implicit val inputStreamWritable: Writable[InputStream] =
    chunkedWritable { is: InputStream => io.chunkR(is) }

  // TODO parameterize chunk size
  implicit def readerWritable(implicit charset: Charset = Charset.`UTF-8`): Writable[Reader] =
    // TODO polish and contribute back to scalaz-stream
    processWritable[Array[Char]].contramap { r: Reader =>
      val unsafeChunkR = io.resource(Task.delay(r))(
        src => Task.delay(src.close())) { src =>
        Task.now { buf: Array[Char] => Task.delay {
          val m = src.read(buf)
          println("BUFFER = "+buf.subSequence(0, m))
          if (m == buf.length) buf
          else if (m == -1) throw End
          else buf.slice(0, m)
        }}
      }
      val chunkR = unsafeChunkR.map(f => (n: Int) => {
        val buf = new Array[Char](n)
        f(buf)
      })
      Process.constant(4096).through(chunkR)
    }

  def chunkedWritable[A](f: A => Channel[Task, Int, ByteVector], chunkSize: Int = 4096): Writable[A] =
    processWritable[ByteVector].contramap { a => Process.constant(chunkSize).through(f(a)) }

  implicit def charRopeWritable(implicit charset: Charset = Charset.`UTF-8`): Writable[Rope[Char]] =
    stringWritable.contramap(_.asString)

  implicit def byteRopeWritable(implicit charset: Charset = Charset.`UTF-8`): Writable[Rope[Byte]] =
     byteArrayWritable.contramap(_.toArray)
}
