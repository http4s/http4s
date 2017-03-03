package org.http4s.internal

import scala.concurrent.duration._
import scalaz.\/
import scalaz.concurrent.{Future, Task}

private[http4s] object compatibility extends CompatibilitySyntax

private[http4s] trait CompatibilitySyntax {
  implicit def http4sTaskCompatibilitySyntax[A](task: Task[A]): TaskCompatibilityOps[A] =
    new TaskCompatibilityOps(task)
  implicit def http4sScalazFutureSyntax[A](future: Future[A]): ScalazFutureCompatibilityOps[A] =
    new ScalazFutureCompatibilityOps(future)
}

private[http4s] final class TaskCompatibilityOps[A](val self: Task[A]) {
  def unsafePerformAsync(f: Throwable \/ A => Unit): Unit =
    self.runAsync(f)

  def unsafePerformSync: A =
    self.run

  def unsafePerformSyncFor(duration: Duration): A =
    self.runFor(duration)

  def unsafePerformSyncAttempt: Throwable \/ A =
    self.attemptRun
}

private[http4s] final class ScalazFutureCompatibilityOps[A](val self: Future[A]) {
  def unsafePerformAsync(f: A => Unit): Unit =
    self.runAsync(f)
}
