package org.http4s

import java.io.{InputStream, File}
import java.nio.ByteBuffer
import java.nio.file.Path
import scala.language.implicitConversions
import scalaz._
import scalaz.concurrent.Task
import scalaz.std.list._
import scalaz.std.option._
import scalaz.stream.{Channel, io, Process}
import scalaz.stream.Process.emit
import scalaz.syntax.apply._
import scodec.bits.ByteVector

import org.http4s.Header.`Content-Type`

case class Writable[-A](
  toEntity: A => Task[Writable.Entity],
  headers: Headers
) {
  def contramap[B](f: B => A): Writable[B] = copy(toEntity = f andThen toEntity)
}

object Writable extends WritableInstances {
  case class Entity(body: EntityBody, length: Option[Int] = None)

  object Entity {
    implicit val entityInstance: Monoid[Entity] = Monoid.instance(
      (a, b) => Entity(a.body ++ b.body, (a.length |@| b.length) { _ + _ }),
      empty
    )

    lazy val empty = Entity(EntityBody.empty, Some(0))
  }

  def simple[A](toChunk: A => ByteVector, headers: Headers = Headers.empty): Writable[A] = Writable(
    toChunk andThen { chunk => Task.now(Entity(emit(chunk), Some(chunk.size))) },
    headers
  )
}

import Writable._

trait WritableInstances1 {
  // TODO This one is in questionable taste
  implicit def seqWritable[A](implicit W: Writable[A]): Writable[Seq[A]] =
    Writable(
      as => Nondeterminism[Task].gather(as.map(W.toEntity)).map(entities => Foldable[List].fold(entities)),
      W.headers
    )
}

trait WritableInstances0 extends WritableInstances1 {
  implicit def showWritable[A](implicit charset: CharacterSet = CharacterSet.`UTF-8`, show: Show[A]): Writable[A] =
    simple(
      a => ByteVector.view(show.shows(a).getBytes(charset.charset)),
      Headers(`Content-Type`.`text/plain`.withCharset(charset))
    )

  implicit def naturalTransformationWritable[F[_], A](implicit W: Writable[A], N: ~>[F, Task]): Writable[F[A]] =
    taskWritable[A].contramap { f: F[A] => N(f) }

  /**
   * A process writable is intended for streaming, and does not calculate its bodies in
   * advance.  As such, it does not calculate the Content-Length in advance.  This is for
   * use with chunked transfer encoding.
   */
  // TODO buggy at bufSize > 1
  // TODO configurable bufSize
  implicit def processWritable[A](implicit W: Writable[A]): Writable[Process[Task, A]] =
    W.copy(toEntity = { process =>
      Task.now(Entity(process.flatMap(a => Process.await(W.toEntity(a))(_.body)), None))
    })
}

trait WritableInstances extends WritableInstances0 {
  implicit def stringWritable(implicit charset: CharacterSet = CharacterSet.`UTF-8`): Writable[String] = simple(
    s => ByteVector.view(s.getBytes(charset.charset)),
    Headers(`Content-Type`.`text/plain`.withCharset(charset))
  )

  implicit val byteVectorWritable: Writable[ByteVector] = simple(
    identity,
    Headers(`Content-Type`.`application/octet-stream`)
  )

  implicit val byteArrayWritable: Writable[Array[Byte]] = byteVectorWritable.contramap(ByteVector.apply)

  implicit val byteBufferWritable: Writable[ByteBuffer] = byteVectorWritable.contramap(ByteVector.apply)

  // TODO split off to module to drop scala-xml core dependency
  // TODO infer HTML, XHTML, etc.
  implicit def htmlWritable(implicit charset: CharacterSet = CharacterSet.`UTF-8`): Writable[xml.Elem] = simple(
    xml => ByteVector.view(xml.buildString(false).getBytes(charset.charset)),
    Headers(`Content-Type`(MediaType.`text/html`).withCharset(charset))
  )

  implicit def taskWritable[A](implicit W: Writable[A]): Writable[Task[A]] =
    W.copy(toEntity = _.flatMap(W.toEntity))

  // TODO parameterize chunk size
  // TODO if Header moves to Entity, can add a Content-Disposition with the filename
  implicit val fileWritable: Writable[File] =
    channelWritable { f: File => io.fileChunkR(f.getAbsolutePath) }

  // TODO parameterize chunk size
  // TODO if Header moves to Entity, can add a Content-Disposition with the filename
  implicit val filePathWritable: Writable[Path] = fileWritable.contramap(_.toFile)

  // TODO parameterize chunk size
  implicit def inputStreamWritable: Writable[InputStream] =
    channelWritable { is: InputStream => io.chunkR(is) }

  def channelWritable[A](f: A => Channel[Task, Int, ByteVector], chunkSize: Int = 4096): Writable[A] =
    processWritable[ByteVector].contramap { a => Process.constant(chunkSize).through(f(a)) }
}
