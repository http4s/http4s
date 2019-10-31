/* Derived from https://raw.githubusercontent.com/etorreborre/specs2/c0cbfc71390b644db1a5deeedc099f74a237ebde/matcher-extra/src/main/scala-scalaz-7.0.x/org/specs2/matcher/TaskMatchers.scala
 * License: https://raw.githubusercontent.com/etorreborre/specs2/master/LICENSE.txt
 */
package org.http4s.testing

import cats.effect.{IO, Sync}
import scala.concurrent.duration.FiniteDuration

/**
  * Matchers for cats.effect.IO
  */
trait IOMatchers extends RunTimedMatchers[IO] {

  protected implicit def F: Sync[IO] = IO.ioEffect
  protected def runWithTimeout[A](fa: IO[A], timeout: FiniteDuration): Option[A] =
    fa.unsafeRunTimed(timeout)
  protected def runAwait[A](fa: IO[A]): A = fa.unsafeRunSync

}

object IOMatchers extends IOMatchers
