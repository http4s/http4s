package org.http4s

import scalaz.stream.Process
import scala.concurrent.{ExecutionContext, Future}
import scalaz.concurrent.Task
import scala.language.implicitConversions

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

  implicit def byteWritable = new SimpleWritable[Array[Byte]] {
    def asChunk(data: Array[Byte]): BodyChunk = BodyChunk(data)
    def contentType: ContentType = ContentType.`application/octet-stream`
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
  */

  implicit def processWritable[A](implicit w: SimpleWritable[A]) = new Writable[Process[Task, A]] {
    def contentType: ContentType = w.contentType

    def toBody(a: Process[Task, A]): Task[(HttpBody, Option[Int])] = Task.now((a.map(w.asChunk), None))
  }

  implicit def seqWritable[A](implicit w: SimpleWritable[A]) = new Writable[Seq[A]] {
    def contentType: ContentType = w.contentType

    def toBody(a: Seq[A]): Task[(HttpBody, Option[Int])] = {
      val p = Process.emit(a.foldLeft(BodyChunk())((acc, c) => acc ++ w.asChunk(c)))
      Task.now((p, None))
    }
  }

  implicit def futureWritable[A](implicit ec: ExecutionContext, writable: Writable[A]) =
    new Writable[Future[A]] {
      def contentType: ContentType = writable.contentType
      def toBody(f: Future[A]) = taskWritable[A].toBody(futureToTask(ec)(f))
    }
}
