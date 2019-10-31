/* Derived from https://raw.githubusercontent.com/etorreborre/specs2/c0cbfc71390b644db1a5deeedc099f74a237ebde/matcher-extra/src/main/scala-scalaz-7.0.x/org/specs2/matcher/TaskMatchers.scala
 * License: https://raw.githubusercontent.com/etorreborre/specs2/master/LICENSE.txt
 */
package org.http4s.testing

import cats.effect.Sync
import org.specs2.matcher._
import org.specs2.matcher.ValueChecks._
import scala.concurrent.duration.FiniteDuration

/**
  * Matchers for cats.effect.F_
  */
trait RunTimedMatchers[F[_]] {

  protected implicit def F: Sync[F]
  protected def runWithTimeout[A](fa: F[A], timeout: FiniteDuration): Option[A]
  protected def runAwait[A](fa: F[A]): A

  // This comes from a private trait in real IOMatchers
  implicit class NotNullSyntax(s: String) {
    def notNull: String =
      Option(s).getOrElse("null")
  }

  def returnOk[T]: TimedMatcher[T] =
    attemptRun(ValueCheck.alwaysOk, None)

  def returnValue[T](check: ValueCheck[T]): TimedMatcher[T] =
    attemptRun(check, None)

  def returnBefore[T](duration: FiniteDuration): TimedMatcher[T] =
    attemptRun(ValueCheck.alwaysOk, Some(duration))

  private def attemptRun[T](
      check: ValueCheck[T],
      duration: Option[FiniteDuration]): TimedMatcher[T] =
    TimedMatcher(check, duration)

  case class TimedMatcher[T](
      check: ValueCheck[T],
      duration: Option[FiniteDuration]
  ) extends Matcher[F[T]] {

    override final def apply[S <: F[T]](expected: Expectable[S]): MatchResult[S] = {

      def checkOrFail[A](res: Either[Throwable, T]): MatchResult[S] = res match {
        case Left(error) =>
          val message = s"an exception was thrown ${error.getMessage.notNull}"
          result(false, message, message, expected)
        case Right(actual) =>
          result(check.check(actual), expected)
      }

      val theAttempt = F.attempt(expected.value)
      duration match {
        case Some(timeout) =>
          runWithTimeout(theAttempt, timeout) match {
            case None =>
              val message = s"Timeout after ${timeout.toMillis} milliseconds"
              result(false, message, message, expected)
            case Some(result) => checkOrFail(result)
          }
        case None =>
          checkOrFail(runAwait(theAttempt))
      }

    }

    def before(d: FiniteDuration): TimedMatcher[T] =
      copy(duration = Some(d))

    def withValue(check: ValueCheck[T]): TimedMatcher[T] =
      copy(check = check)

    def withValue(t: T): TimedMatcher[T] =
      withValue(valueIsTypedValueCheck(t))

  }
}
