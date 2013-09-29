package org.http4s

import scala.language.implicitConversions
import concurrent.{ExecutionContext, Future}
import scalaz.stream.Process
import scalaz.concurrent.Task
import scalaz.syntax.monad._
import scalaz.Functor
import java.nio.charset.Charset
import scala.io.Codec

trait Writable[+F[_], -A] {
  def contentType: ContentType
  def toBody(a: A): (HttpBody[F], Option[Int])
}

trait SimpleWritable[+F[_], -A] extends Writable[F, A] {
  def asChunk(data: A): BodyChunk
  override def toBody(a: A): (HttpBody[F], Option[Int]) = {
    val chunk = asChunk(a)
    (Process.emit(chunk), Some(chunk.length))
  }
}

object Writable {
  // Simple types defined
  implicit def stringWritable[F[_]](implicit charset: HttpCharset = HttpCharsets.`UTF-8`) =
    new SimpleWritable[F, String] {
      def contentType: ContentType = ContentType.`text/plain`.withCharset(charset)
      def asChunk(s: String) = BodyChunk(s, charset.nioCharset)
    }

  implicit def htmlWritable[F[_]](implicit charset: HttpCharset = HttpCharsets.`UTF-8`) =
    new SimpleWritable[F, xml.Elem] {
      def contentType: ContentType = ContentType(MediaTypes.`text/html`).withCharset(charset)
      def asChunk(s: xml.Elem) = BodyChunk(s.buildString(false), charset.nioCharset)
    }

  implicit def intWritable[F[_]](implicit charset: HttpCharset = HttpCharsets.`UTF-8`) =
    new SimpleWritable[F, Int] {
      def contentType: ContentType = ContentType.`text/plain`.withCharset(charset)
      def asChunk(i: Int) = BodyChunk(i.toString, charset.nioCharset)
    }

  implicit def functorWritable[F[_], A](implicit F: Functor[F], writable: Writable[F, A]) =
    new Writable[F, F[A]] {
      def contentType = writable.contentType
      private def send(fa: F[A]) = Process.emit(fa.map(writable.toBody(_)._1)).eval.join
      override def toBody(fa: F[A]) = (send(fa), None)
    }
}
