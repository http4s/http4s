/* Derived from https://raw.githubusercontent.com/etorreborre/specs2/c0cbfc71390b644db1a5deeedc099f74a237ebde/matcher-extra/src/main/scala-scalaz-7.0.x/org/specs2/matcher/TaskMatchers.scala
 * License: https://raw.githubusercontent.com/etorreborre/specs2/master/LICENSE.txt
 */
package org.http4s.testing

import cats.effect.IO
import org.specs2.matcher.ValueChecks._
import org.specs2.matcher._

import scala.concurrent.duration.FiniteDuration

/**
 * Matchers for cats.effect.IO
 */
trait IOMatchers {
  // This comes from a private trait in real IOMatchers
  implicit class NotNullSyntax(s: String) {
    def notNull: String =
      Option(s).getOrElse("null")
  }

  def returnOk[T]: IOMatcher[T] =
    attemptRun(ValueCheck.alwaysOk, None)

  def returnValue[T](check: ValueCheck[T]): IOMatcher[T] =
    attemptRun(check, None)

  def returnBefore[T](duration: FiniteDuration): IOMatcher[T] =
    attemptRun(ValueCheck.alwaysOk, Some(duration))

  private def attemptRun[T](check: ValueCheck[T], duration: Option[FiniteDuration]): IOMatcher[T] =
   IOMatcher(check, duration)

  case class IOMatcher[T](check: ValueCheck[T], duration: Option[FiniteDuration]) extends Matcher[IO[T]] {
    def apply[S <: IO[T]](e: Expectable[S]): MatchResult[S] = {
      duration match {
        case Some(d) =>
          e.value
            .attempt
            .unsafeRunTimed(d)
            .fold(failedAttemptWithTimeout(e, d))(_.fold(failedAttempt(e), checkResult(e)))
        case None => e.value.attempt.unsafeRunSync.fold(failedAttempt(e), checkResult(e))
      }
    }

    def before(d: FiniteDuration): IOMatcher[T] =
      copy(duration = Some(d))

    def withValue(check: ValueCheck[T]): IOMatcher[T] =
      copy(check = check)

    def withValue(t: T): IOMatcher[T] =
      withValue(valueIsTypedValueCheck(t))

    private def failedAttemptWithTimeout[S <: IO[T]](e: Expectable[S], d: FiniteDuration): MatchResult[S] = {
      val message = s"Timeout after ${d.toMillis} milliseconds"
      result(false, message, message, e)
    }

    private def failedAttempt[S <: IO[T]](e: Expectable[S])(t: Throwable): MatchResult[S] = {
      val message = s"an exception was thrown ${t.getMessage.notNull}"
      result(false, message, message, e)
    }

    private def checkResult[S <: IO[T]](e: Expectable[S])(t: T): MatchResult[S] =
      result(check.check(t), e)

  }
}

object IOMatchers extends IOMatchers
