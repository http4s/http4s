package org.http4s.util

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Success, Failure}
import scalaz.{~>, \/-, -\/}
import scalaz.concurrent.Task
import scalaz.syntax.id._

trait TaskInstances {
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
}

object task extends TaskInstances
