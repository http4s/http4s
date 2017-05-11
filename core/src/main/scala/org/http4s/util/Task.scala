package org.http4s.util

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Success, Failure}
import scalaz.{-\/, \/-}
import scalaz.syntax.either._
import scalaz.concurrent.Task

import org.http4s.internal.compatibility._

trait TaskFunctions {
  /** A task that never completes */
  val never: Task[Nothing] =
    Task.async(_ => ())

  def unsafeTaskToFuture[A](task: Task[A]): Future[A] = {
    val p = Promise[A]()
    task.unsafePerformAsync {
      case \/-(a) =>
        p.success(a)
        ()
      case -\/(t) =>
        p.failure(t)
        ()
    }
    p.future
  }

  def futureToTask[A](f: => Future[A])(implicit ec: ExecutionContext): Task[A] = {
    Task.async { cb =>
      f.onComplete {
        case Success(a) => cb(a.right)
        case Failure(t) => cb(t.left)
      }
    }
  }
}

object task extends TaskFunctions
