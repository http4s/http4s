package org.http4s

import scala.language.implicitConversions

trait Writable[-A] {
  def contentType: ContentType
  def asRaw(a: A): Raw
}

object Writable {
  implicit def stringWritable(implicit charset: HttpCharset = HttpCharsets.`UTF-8`) =
    new Writable[String] {
      def contentType: ContentType = ContentType.`text/plain`.withCharset(charset)
      def asRaw(s: String): Raw = s.getBytes(charset.nioCharset)
    }

  implicit def intWritable(implicit charset: HttpCharset = HttpCharsets.`UTF-8`) =
    new Writable[Int] {
      def contentType: ContentType = ContentType.`text/plain`.withCharset(charset)
      def asRaw(i: Int): Raw = i.toString.getBytes(charset.nioCharset)
    }

  implicit def rawWritable =
    new Writable[Raw] {
      def contentType: ContentType = ContentType.`application/octet-stream`
      def asRaw(raw: Raw) = raw
    }

  implicit def traversableWritable[A](implicit writable:Writable[A]) =
    new Writable[TraversableOnce[A]] {
      def contentType: ContentType = writable.contentType
      def asRaw(as: TraversableOnce[A]): Raw = as.foldLeft(Array.empty[Byte]) { (acc, a) => acc ++ writable.asRaw(a) }
    }
}

