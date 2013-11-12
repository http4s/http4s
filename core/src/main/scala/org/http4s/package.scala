package org

import http4s.ext.Http4sString
import scala.concurrent.{ExecutionContext, Promise, Future}
import com.typesafe.config.{ConfigFactory, Config}
import scalaz.{-\/, \/-, Semigroup, ~>}
import scalaz.concurrent.Task
import scalaz.syntax.id._
import scalaz.stream.Process
import scala.util.{Failure, Success}
import org.joda.time.{DateTime, DateTimeZone, ReadableInstant}
import org.joda.time.format.DateTimeFormat
import java.util.Locale
import org.http4s.util.{LowercaseEn, LowercaseSyntax}
import scalaz.@@

package object http4s extends LowercaseSyntax {
  type HttpService = Request => Task[Response]
  type HttpBody = Process[Task, Chunk]

  implicit val ChunkSemigroup: Semigroup[Chunk] = Semigroup.instance {
    case (a: BodyChunk, b: BodyChunk) => a ++ b
    case (a: BodyChunk, _) => a
    case (_, b: BodyChunk) => b
    case (_, _) => BodyChunk.empty
  }
  
  private[http4s] implicit def string2Http4sString(s: String) = new Http4sString(s)

  protected[http4s] val Http4sConfig: Config = ConfigFactory.load()

  implicit val taskToFuture: Task ~> Future = new (Task ~> Future) {
    def apply[A](task: Task[A]): Future[A] = {
      val p = Promise[A]()
      task.runAsync {
        case \/-(a) => p.success(a)
        case -\/(t) => p.failure(t)
      }
      p.future
    }
  }

  implicit def futureToTask(implicit ec: ExecutionContext): Future ~> Task = new (Future ~> Task) {
    def apply[A](future: Future[A]): Task[A] = {
      Task.async { f =>
        future.onComplete {
          case Success(a) => f(a.right)
          case Failure(t) => f(t.left)
        }
      }
    }
  }

  private[this] val Rfc1123Format = DateTimeFormat
    .forPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'")
    .withLocale(Locale.US)
    .withZone(DateTimeZone.UTC);

  implicit class RichReadableInstant(instant: ReadableInstant) {
    def formatRfc1123: String = Rfc1123Format.print(instant)
  }

  val UnixEpoch = new DateTime(0)
}
