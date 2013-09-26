package org.http4s

import scala.language.implicitConversions
import concurrent.{ExecutionContext, Future}
import akka.util.ByteString
import scalaz.stream.Process
import scalaz.concurrent.Task
import scalaz.syntax.monad._
import scalaz.Functor

trait Writable[+F[_], -A] {
  def contentType: ContentType
  def toBody(a: A): (HttpBody[F], Option[Int])
}

trait SimpleWritable[+F[_], -A] extends Writable[F, A] {
  def asByteString(data: A): ByteString
  override def toBody(a: A): (HttpBody[F], Option[Int]) = {
    val bs = asByteString(a)
    (Writable.sendByteString(bs), Some(bs.length))
  }
}

object Writable {
  private[http4s] def sendByteString[F[_]](data: ByteString): HttpBody[F] = Process.emit(BodyChunk(data))

  // Simple types defined
  implicit def stringWritable[F[_]](implicit charset: HttpCharset = HttpCharsets.`UTF-8`) =
    new SimpleWritable[F, String] {
      def contentType: ContentType = ContentType.`text/plain`.withCharset(charset)
      def asByteString(s: String) = ByteString(s, charset.nioCharset.name)
    }

  implicit def htmlWritable[F[_]](implicit charset: HttpCharset = HttpCharsets.`UTF-8`) =
    new SimpleWritable[F, xml.Elem] {
      def contentType: ContentType = ContentType(MediaTypes.`text/html`).withCharset(charset)
      def asByteString(s: xml.Elem) = ByteString(s.buildString(false), charset.nioCharset.name)
    }

  implicit def intWritable[F[_]](implicit charset: HttpCharset = HttpCharsets.`UTF-8`) =
    new SimpleWritable[F, Int] {
      def contentType: ContentType = ContentType.`text/plain`.withCharset(charset)
      def asByteString(i: Int): ByteString = ByteString(i.toString, charset.nioCharset.name)
    }

  implicit def ByteStringWritable[F[_]] =
    new SimpleWritable[F, ByteString] {
      def contentType: ContentType = ContentType.`application/octet-stream`
      def asByteString(ByteString: ByteString) = ByteString
    }

  // More complex types can be implements in terms of simple types
  implicit def traversableWritable[F[_], A](implicit writable: SimpleWritable[F, A]) =
    new Writable[F, TraversableOnce[A]] {
      def contentType: ContentType = writable.contentType
      override def toBody(as: TraversableOnce[A]) = {
        val bs = as.foldLeft(ByteString.empty) { (acc, a) => acc ++ writable.asByteString(a) }
        (sendByteString(bs), Some(bs.length))
      }
    }

  implicit def functorWritable[F[_], A](implicit F: Functor[F], writable: Writable[F, A]) =
    new Writable[F, F[A]] {
      def contentType = writable.contentType
      private def send(fa: F[A]) = Process.emit(fa.map(writable.toBody(_)._1)).eval.join
      override def toBody(fa: F[A]) = (send(fa), None)
    }
}
