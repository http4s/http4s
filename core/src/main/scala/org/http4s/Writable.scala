package org.http4s

import scala.language.implicitConversions
import play.api.libs.iteratee._
import concurrent.{ExecutionContext, Future}
import akka.util.ByteString

trait Writable[-A] {
  def contentType: ContentType
  def toBody(a: A): Enumeratee[HttpChunk, HttpChunk]
}

trait SimpleWritable[-A] extends Writable[A] {
  def asRaw(data: A): Raw
  override def toBody(a: A): Enumeratee[HttpChunk, HttpChunk] = Writable.sendRaw(HttpEntity(asRaw(a)))
}

object Writable {
  private[http4s] def sendRaw[T](data: T): Enumeratee[T, T] = new Enumeratee[T, T] {
    def applyOn[A](inner: Iteratee[T, A]): Iteratee[T, Iteratee[T, A]] =
      Done(Iteratee.flatten(inner.feed(Input.El(data))), Input.Empty)
  }

  private[http4s] def sendFuture[T](f: Future[T])(implicit ec: ExecutionContext): Enumeratee[T, T] =
    new Enumeratee[T, T] {
      def applyOn[A](inner: Iteratee[T, A]): Iteratee[T, Iteratee[T, A]] =
        Done(Iteratee.flatten(f.flatMap{ d => inner.feed(Input.El(d))}))
    }

  private[http4s] def sendEnumerator[F, T](enumerator: Enumerator[T]): Enumeratee[F, T] = new Enumeratee[F, T] {
    def applyOn[A](inner: Iteratee[T, A]): Iteratee[F, Iteratee[T, A]] =
      Done(Iteratee.flatten(enumerator(inner)), Input.Empty)
  }
  // Simple types defined
  implicit def stringWritable(implicit charset: HttpCharset = HttpCharsets.`UTF-8`) =
    new SimpleWritable[String] {
      def contentType: ContentType = ContentType.`text/plain`.withCharset(charset)
      def asRaw(s: String) = ByteString(s, charset.nioCharset.name)
    }

  implicit def intWritable(implicit charset: HttpCharset = HttpCharsets.`UTF-8`) =
    new SimpleWritable[Int] {
      def contentType: ContentType = ContentType.`text/plain`.withCharset(charset)
      def asRaw(i: Int): Raw = ByteString(i.toString, charset.nioCharset.name)
    }

  implicit def rawWritable =
    new SimpleWritable[Raw] {
      def contentType: ContentType = ContentType.`application/octet-stream`
      def asRaw(raw: Raw) = raw
    }

//  implicit def chunkWritable =      // Perhaps if we are this far, it should be handled manually anyway.
//    new SimpleWritable[HttpChunk] {
//      def contentType: ContentType = ContentType.`application/octet-stream`
//      def asRaw(chunk: HttpChunk) = chunk.bytes
//    }

  // More complex types can be implements in terms of simple types
  implicit def enumerateeWritable =
  new Writable[Enumeratee[HttpChunk, HttpChunk]] {
    def contentType = ContentType.`application/octet-stream`
    override def toBody(a: Enumeratee[HttpChunk, HttpChunk])= a
  }

  implicit def enumeratorWritable[A](implicit writable: SimpleWritable[A]) =
  new Writable[Enumerator[A]] {
    def contentType = writable.contentType
    override def toBody(a: Enumerator[A]) = sendEnumerator(a.map[HttpChunk]{ i => HttpEntity(writable.asRaw(i)) })
  }

  implicit def futureWritable[A](implicit writable: SimpleWritable[A], ec: ExecutionContext) =
  new Writable[Future[A]] {
    def contentType = writable.contentType
    override def toBody(f: Future[A]) = sendFuture(f.map{ d => HttpEntity(writable.asRaw(d))})
  }

  implicit def traversableWritable[A](implicit writable: SimpleWritable[A]) =
    new SimpleWritable[TraversableOnce[A]] {
      def contentType: ContentType = writable.contentType
      def asRaw(as: TraversableOnce[A]): Raw = as.foldLeft(ByteString.empty) { (acc, a) => acc ++ writable.asRaw(a) }
    }
}
