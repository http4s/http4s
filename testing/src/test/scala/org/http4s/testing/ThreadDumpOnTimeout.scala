package org.http4s
package testing

import org.specs2.concurrent.ExecutionEnv
import org.specs2.control.Debug._
import org.specs2.execute._
import org.specs2.matcher.TerminationMatchers._
import org.specs2.matcher._
import org.specs2.specification.{Around, Context, EachContext}
import org.specs2.specification.core._

import scala.collection.JavaConverters._
import scala.concurrent.duration._

/**
 * This trait can be used to add a global time out to each example or for a specific one:
 *
 *  - for each example mix-in the trait
 *  - for a single example import the object and use the upTo context:
 *
 *   my example must terminate in a reasonable amount of time \${upTo(3.seconds)(e1)}
 */
trait ThreadDumpOnTimeout extends EachContext {

  /**
   * Return number blocking waiting periods to partition the timeout into.
   *
   * `ThreadDumpOnTimeout` uses {{TerminationMatchers.terminate()}}, which calls
   * `Thread.sleep()` for a period of time and checks if the spec is still running
   * when `sleep()` returns. If we have a long timeout, say 30 seconds, and do not
   * slice it up, the spec will take 30 seconds to complete even if the real work
   * finished after 100 milliseconds.
   */
  def slices: Int = 10

  /**
   * Overall time to wait before triggering a thread dump.
   *
   * Spec will take ''at least'' `timeout / slices` to complete when `ThreadDumpOnTimeout` is mixed in.
   */
  def triggerThreadDumpAfter: Duration = 5.seconds

  def context: Env => Context = { env: Env =>
    aroundTimeout(triggerThreadDumpAfter)(env.executionEnv)
  }

  // borrows heavily from `ExamplesTimeout` in specs2
  def aroundTimeout(to: Duration)(implicit ee: ExecutionEnv): Around =
    new Around {
      def around[T : AsResult](t: =>T): Result = {
        lazy val result = t
        val exp: Expectable[T] = createExpectable(result)
        val outcome = terminate(retries = slices, sleep = (to.toMillis / slices).millis)(ee)(exp)
        threadDumpOnFailure(outcome)
        AsResult(result)
      }
    }


  private def threadDumpOnFailure[T](result: MatchResult[T]) = result match {
    case _: MatchFailure[_] => formatThreadDump(Thread.getAllStackTraces.asScala.toMap).pp
    case _ => ()
  }

  def formatThreadDump(threadDump: Map[Thread, Array[StackTraceElement]]): String = {
    def expandFrames(frames: Iterable[StackTraceElement]) =
      frames.map(f => s"  $f\n").mkString

    val dumpedThreads = Thread.getAllStackTraces.asScala
      .map(e => s"${e._1}\n${expandFrames(e._2)}")

    s"Triggering thread dump after $triggerThreadDumpAfter:\n\n${dumpedThreads.mkString("\n")}"
  }
}
