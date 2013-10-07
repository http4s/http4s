package org.http4s

import scalaz.stream.Process
import scalaz.syntax.monad._
import scalaz.Functor

trait Writable[-A] {
  def contentType: ContentType
  def toBody(a: A): (HttpBody, Option[Int])
}

trait SimpleWritable[-A] extends Writable[A] {
  def asChunk(data: A): BodyChunk
  override def toBody(a: A): (HttpBody, Option[Int]) = {
    val chunk = asChunk(a)
    (Process.emit(chunk), Some(chunk.length))
  }
}

object Writable {
  // Simple types defined
  implicit def stringWritable(implicit charset: HttpCharset = HttpCharsets.`UTF-8`) =
    new SimpleWritable[String] {
      def contentType: ContentType = ContentType.`text/plain`.withCharset(charset)
      def asChunk(s: String) = BodyChunk(s, charset.nioCharset)
    }

  implicit def htmlWritable(implicit charset: HttpCharset = HttpCharsets.`UTF-8`) =
    new SimpleWritable[xml.Elem] {
      def contentType: ContentType = ContentType(MediaTypes.`text/html`).withCharset(charset)
      def asChunk(s: xml.Elem) = BodyChunk(s.buildString(false), charset.nioCharset)
    }

  implicit def intWritable(implicit charset: HttpCharset = HttpCharsets.`UTF-8`) =
    new SimpleWritable[Int] {
      def contentType: ContentType = ContentType.`text/plain`.withCharset(charset)
      def asChunk(i: Int) = BodyChunk(i.toString, charset.nioCharset)
    }
/*
  implicit def functorWritable[F[_], A](implicit F: Functor[F], writable: Writable[A]) =
    new Writable[F[A]] {
      def contentType = writable.contentType
      private def send(fa: F[A]) = Process.emit(fa.map(writable.toBody(_)._1)).eval.join
      override def toBody(fa: F[A]) = (send(fa), None)
    }
    */
}
