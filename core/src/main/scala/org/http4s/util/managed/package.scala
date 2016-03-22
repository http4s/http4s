package org.http4s.util

import scalaz.Codensity
import scalaz.concurrent.Task

package object managed {
  type Managed[A] = Codensity[Task, A]

  private[this] val logger = org.log4s.getLogger

  def manage[R](r: => R)(implicit R: Manageable[R]): Managed[R] =
    bracket(Task.delay(r))(R.shutdown)

  def bracket[R](acquire: Task[R])(shutdown: R => Task[Unit]): Managed[R] =
    new Codensity[Task, R] {
      def apply[A](f: R => Task[A]): Task[A] =
        acquire.flatMap { r =>
          logger.info(s"Acquired managed resource: $r")
          f(r).onFinish {
            case _ =>
              logger.info(s"Shutting down managed resource: $r")
              shutdown(r).handle {
                case t: Throwable =>
                  logger.error(t)("Error closing managed resource")
              }
          }
        }
    }
}
