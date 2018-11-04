package org.http4s
package util

import org.http4s.testing.ErrorReportingUtils
import org.specs2.mutable.Specification

import scala.concurrent.ExecutionContext

abstract class ExecutionSpec extends Specification with ErrorReportingUtils {
  def ec: ExecutionContext
  def ecName: String

  def toRunnable(f: => Unit): Runnable = new Runnable {
    override def run(): Unit = f
  }

  def submit(f: => Unit): Unit = ec.execute(toRunnable(f))

  ecName should {
    "submit a working job" in {
      var i = 0

      submit {
        i += 1
      }

      (i must be).equalTo(1)
    }

    "submit multiple working jobs" in {
      var i = 0

      for (_ <- 0 until 10) {
        submit {
          i += 1
        }
      }

      (i must be).equalTo(10)
    }

    "submit jobs from within a job" in {
      var i = 0

      submit {
        for (_ <- 0 until 10) {
          submit {
            i += 1
          }
        }
      }

      (i must be).equalTo(10)
    }

    "submit a failing job" in {
      val i = 0

      silenceSystemErr {
        submit {
          sys.error("Boom")
        }
      }

      (i must be).equalTo(0)
    }

    "interleave failing and successful `Runnables`" in {
      var i = 0

      silenceSystemErr {
        submit {
          for (j <- 0 until 10) {
            submit {
              if (j % 2 == 0) submit { i += 1 } else submit { sys.error("Boom") }
            }
          }
        }
      }

      (i must be).equalTo(5)
    }
  }
}

class TrampolineSpec extends ExecutionSpec {
  def ec = execution.trampoline
  def ecName = "trampoline"

  "trampoline" should {
    "Not blow the stack" in {
      val iterations = 500000
      var i = 0

      def go(j: Int): Unit = submit {
        if (j < iterations) {
          i += 1
          go(j + 1)
        }
      }

      go(0)

      (i must be).equalTo(iterations)
    }
  }
}

class DirectSpec extends ExecutionSpec {
  def ec = execution.direct
  def ecName = "direct"
}
