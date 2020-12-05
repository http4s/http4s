/*
 * Copyright 2016 http4s.org
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

import cats.effect.Blocker
import org.http4s.internal.threads.newBlockingPool
import munit._

/** Common stack for http4s' munit based tests
  */
trait Http4sSuite extends CatsEffectSuite with DisciplineSuite with munit.ScalaCheckEffectSuite {

  val testBlocker: Blocker = Http4sSpec.TestBlocker
}

object Http4sSuite {
  val TestBlocker: Blocker =
    Blocker.liftExecutorService(newBlockingPool("http4s-spec-blocking"))
}
