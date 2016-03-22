package org.http4s.util.managed

import java.io.Closeable
import java.util.concurrent.ExecutorService

import scalaz.concurrent.Task

trait Manageable[A] {
  def shutdown(a: A): Task[Unit]
}

object Manageable extends ManageableInstances {
  def fromShutdown[A](f: A => Task[Unit]): Manageable[A] =
    new Manageable[A] {
      def shutdown(a: A): Task[Unit] =
        Task.delay(f(a))
    }
}

trait ManageableInstances {
  implicit val executorServiceIsManageable: Manageable[ExecutorService] =
    Manageable.fromShutdown(a => Task.delay(a.shutdown()))

  implicit def closeableIsManageable[A <: Closeable](a: A): Manageable[A] =
    Manageable.fromShutdown(a => Task.delay(a.close()))
}
