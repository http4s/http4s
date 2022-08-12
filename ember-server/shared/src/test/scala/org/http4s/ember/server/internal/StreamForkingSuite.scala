/*
 * Copyright 2019 http4s.org
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

package org.http4s.ember.server.internal

import cats.effect.IO
import cats.effect.kernel.Deferred
import cats.effect.kernel.Ref
import fs2.Stream
import munit._

import scala.concurrent.duration._

class StreamForkingSuite extends CatsEffectSuite {

  import StreamForking.forking

  test("forking stream completes after outer and inner streams finalize") {
    Ref
      .of[IO, Int](0)
      .flatMap { counter =>
        val finalizer = Stream.bracket(IO.unit)(_ => counter.update(_ + 1))

        val stream = finalizer >> Stream(
          finalizer >> Stream.empty
        )

        forking(stream).compile.drain >> counter.get
      }
      .assertEquals(2)
  }

  test("outer stream can terminate and finalize before inner streams complete") {
    Deferred[IO, Unit]
      .flatMap { gate =>
        val stream = Stream.bracket(IO.unit)(_ => gate.complete(()).void) >> Stream(
          Stream.eval(gate.get)
        )

        forking(stream).compile.drain
      }
      .assertEquals(())
  }

  test("outer stream fails forking stream") {
    val stream = Stream(
      Stream.sleep_[IO](1.minute)
    ) ++ Stream.raiseError[IO](new RuntimeException)

    forking(stream).compile.drain
      .intercept[RuntimeException]
  }

  test("inner stream fails forking stream") {
    val stream = Stream(
      Stream.sleep_[IO](1.minute),
      Stream.raiseError[IO](new RuntimeException),
    )

    forking(stream).compile.drain
      .intercept[RuntimeException]
  }

  test("inner stream cancels") {
    IO.ref(false)
      .flatMap { ref =>
        val stream = Stream(
          Stream.eval(IO.canceled),
          Stream.eval(ref.set(true)),
        )

        // canceled stream should still return semaphore permit
        // and allow second eval to run
        forking(stream, 1).compile.drain >> ref.get
      }
      .assertEquals(true)
  }

}
