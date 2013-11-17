package org.http4s

import scalaz.stream.Process
import scalaz.syntax.monad._
import scala.concurrent.{ExecutionContext, Future}
import scalaz.concurrent.Task
import scala.language.implicitConversions
import util.Execution.{overflowingExecutionContext => oec}

trait Writable[-A] {
  def contentType: ContentType
  def toBody(a: A): Task[(HttpBody, Option[Int])]
}

trait SimpleWritable[-A] extends Writable[A] {
  def asChunk(data: A): BodyChunk
  override def toBody(a: A): Task[(HttpBody, Option[Int])] = {
    val chunk = asChunk(a)
    Task.now(Process.emit(chunk), Some(chunk.length))
  }
}

object Writable {
  // Simple types defined
  implicit def stringWritable(implicit charset: CharacterSet = CharacterSet.`UTF-8`) =
    new SimpleWritable[String] {
      def contentType: ContentType = ContentType.`text/plain`.withCharset(charset)
      def asChunk(s: String) = BodyChunk(s, charset.charset)
    }

  implicit def htmlWritable(implicit charset: CharacterSet = CharacterSet.`UTF-8`) =
    new SimpleWritable[xml.Elem] {
      def contentType: ContentType = ContentType(MediaType.`text/html`).withCharset(charset)
      def asChunk(s: xml.Elem) = BodyChunk(s.buildString(false), charset.charset)
    }

  implicit def intWritable(implicit charset: CharacterSet = CharacterSet.`UTF-8`) =
    new SimpleWritable[Int] {
      def contentType: ContentType = ContentType.`text/plain`.withCharset(charset)
      def asChunk(i: Int) = BodyChunk(i.toString, charset.charset)
    }

  implicit def taskWritable[A](implicit writable: Writable[A]) =
    new Writable[Task[A]] {
      def contentType: ContentType = writable.contentType
      def toBody(a: Task[A]) = a.flatMap(writable.toBody(_))
    }

/*
  implicit def functorWritable[F[_], A](implicit F: Functor[F], writable: Writable[A]) =
    new Writable[F[A]] {
      def contentType = writable.contentType
      private def send(fa: F[A]) = Process.emit(fa.map(writable.toBody(_)._1)).eval.join
      override def toBody(fa: F[A]) = (send(fa), None)
    }

  implicit def enumerateeWritable =
  new Writable[Enumeratee[Chunk, Chunk]] {
    def contentType = ContentType.`application/octet-stream`
    override def toBody(a: Enumeratee[Chunk, Chunk])= (a, None)
  }

  implicit def genericEnumerateeWritable[A](implicit writable: SimpleWritable[A], ec: ExecutionContext) =
    new Writable[Enumeratee[Chunk, A]] {
      def contentType = writable.contentType

      def toBody(a: Enumeratee[Chunk, A]): (Enumeratee[Chunk, Chunk], Option[Int]) = {
        val finalenum = a.compose(Enumeratee.map[A]( i => BodyChunk(writable.asByteString(i)): Chunk))
        (finalenum , None)
      }
    }

  implicit def enumeratorWritable[A](implicit writable: SimpleWritable[A]) =
  new Writable[Enumerator[A]] {
    def contentType = writable.contentType
    override def toBody(a: Enumerator[A]) = (sendEnumerator(a.map[Chunk]{ i => BodyChunk(writable.asByteString(i))}(oec)), None)
  }
*/

  implicit def futureWritable[A](implicit ec: ExecutionContext, writable: Writable[A]) =
    new Writable[Future[A]] {
      def contentType: ContentType = writable.contentType
      def toBody(f: Future[A]) = taskWritable[A].toBody(futureToTask(ec)(f))
    }
}
