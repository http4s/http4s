package org.http4s.util

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Success, Failure}

import fs2._

trait TaskFunctions {
  def unsafeTaskToFuture[A](task: Task[A]): Future[A] = {
    val p = Promise[A]()
    task.unsafeRunAsync {
      case Right(a) => p.success(a)
      case Left(t) => p.failure(t)
    }
    p.future
  }

  def futureToTask[A](f: => Future[A])(implicit ec: ExecutionContext): Task[A] = {
    implicit val strategy = Strategy.fromExecutionContext(ec)
    Task.async { cb =>
      f.onComplete {
        case Success(a) => cb(Right(a))
        case Failure(t) => cb(Left(t))
      }
    }
  }
}

object task extends TaskFunctions
