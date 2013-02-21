package org.http4s

import scala.language.implicitConversions
import play.api.libs.iteratee._
import concurrent.{ExecutionContext, Future}

trait Writable[-A] {
  def contentType: ContentType
  def asRaw(data: A): Raw
  def toBody(a: A): Enumeratee[HttpChunk, HttpChunk] = Writable.feed(HttpEntity(asRaw(a)))
}

object Writable {
  private[Writable] def feed[T](data: T): Enumeratee[T, T] = new Enumeratee[T, T] {
    def applyOn[A](inner: Iteratee[T, A]): Iteratee[T, Iteratee[T, A]] =
      Done(Iteratee.flatten(inner.feed(Input.El(data))), Input.Empty)
  }

  private[Writable] def feedFuture[T](f: Future[T])(implicit ec: ExecutionContext): Enumeratee[T, T] =
    new Enumeratee[T, T] {
      def applyOn[A](inner: Iteratee[T, A]): Iteratee[T, Iteratee[T, A]] =
        Done(Iteratee.flatten(f.flatMap{ d => inner.feed(Input.El(d))}))
    }

  private[Writable] def replace[F, T](enumerator: Enumerator[T]): Enumeratee[F, T] = new Enumeratee[F, T] {
    def applyOn[A](inner: Iteratee[T, A]): Iteratee[F, Iteratee[T, A]] =
      Done(Iteratee.flatten(enumerator(inner)), Input.Empty)
  }

  implicit def stringWritable(implicit charset: HttpCharset = HttpCharsets.`UTF-8`) =
    new Writable[String] {
      def contentType: ContentType = ContentType.`text/plain`.withCharset(charset)
      def asRaw(s: String) = s.getBytes(charset.nioCharset)
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

  implicit def enumerateeWritable =
  new Writable[Enumeratee[HttpChunk, HttpChunk]] {
    def contentType = ContentType.`application/octet-stream`
    def asRaw(raw: Enumeratee[HttpChunk, HttpChunk]) = sys.error("Cannot transform Enumeratee to Raw!")
    override def toBody(a: Enumeratee[HttpChunk, HttpChunk])= a
  }

  implicit def enumeratorWritable[A](implicit writable: Writable[A]) =
  new Writable[Enumerator[A]] {
    def contentType = writable.contentType
    def asRaw(raw: Enumerator[A]) = sys.error("Cannot convert Enumerator to raw!")
    override def toBody(a: Enumerator[A]) = replace(a.map[HttpChunk]{ i => HttpEntity(writable.asRaw(i)) })
  }

  implicit def futureWritable[A](implicit writable: Writable[A], ec: ExecutionContext) =
  new Writable[Future[A]] {
    def contentType = writable.contentType
    def asRaw(raw: Future[A]) = sys.error("Cannot convert Future to raw!")
    override def toBody(f: Future[A]) = feedFuture(f.map{ d => HttpEntity(writable.asRaw(d))})
  }

  implicit def traversableWritable[A](implicit writable:Writable[A]) =
    new Writable[TraversableOnce[A]] {
      def contentType: ContentType = writable.contentType
      def asRaw(as: TraversableOnce[A]): Raw = as.foldLeft(Array.empty[Byte]) { (acc, a) => acc ++ writable.asRaw(a) }
    }
}
