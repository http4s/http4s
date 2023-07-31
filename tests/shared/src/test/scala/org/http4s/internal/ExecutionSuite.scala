/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package internal

import org.http4s.testing.ErrorReporting._

import scala.concurrent.ExecutionContext

@deprecated("Supports an unused feature.  Will be removed in 1.0", "0.23.11")
abstract class ExecutionSuite extends Http4sSuite {
  def ec: ExecutionContext
  def ecName: String

  def toRunnable(f: => Unit): Runnable =
    new Runnable {
      override def run(): Unit = f
    }

  def submit(f: => Unit): Unit = ec.execute(toRunnable(f))

  test(s"$ecName should submit a working job") {
    var i = 0

    submit {
      i += 1
    }

    assertEquals(i, 1)
  }

  test(s"$ecName should submit multiple working jobs") {
    var i = 0

    for (_ <- 0 until 10)
      submit {
        i += 1
      }

    assertEquals(i, 10)
  }

  test(s"$ecName should submit jobs from within a job") {
    var i = 0

    submit {
      for (_ <- 0 until 10)
        submit {
          i += 1
        }
    }

    assertEquals(i, 10)
  }

  test(s"$ecName should submit a failing job") {
    val i = 0

    silenceSystemErr {
      submit {
        sys.error("Boom")
      }
    }

    assertEquals(i, 0)
  }

  test(s"$ecName should interleave failing and successful `Runnables`") {
    var i = 0

    silenceSystemErr {
      submit {
        for (j <- 0 until 10)
          submit {
            if (j % 2 == 0) submit(i += 1)
            else submit(sys.error("Boom"))
          }
      }
    }

    assertEquals(i, 5)
  }

}

@deprecated("Unused.  Will be removed in 1.0", "0.23.11")
class TrampolineSuite extends ExecutionSuite {
  def ec = Trampoline
  def ecName = "trampoline"

  test("trampoline should Not blow the stack") {
    val iterations = 500000
    var i = 0

    def go(j: Int): Unit =
      submit {
        if (j < iterations) {
          i += 1
          go(j + 1)
        }
      }

    go(0)

    assertEquals(i, iterations)
  }
}
