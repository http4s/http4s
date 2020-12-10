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

package org.http4s.testing

import org.specs2.specification.AroundEach
import org.specs2.execute.{AsResult, Result}
import org.http4s.testing.ErrorReporting._

/** Wraps around each test and silences System.out and System.err output streams.
  * Restores back the original streams after each test case.
  */

trait SilenceOutputStream extends AroundEach {

  def around[R: AsResult](r: => R): Result =
    silenceOutputStreams {
      AsResult(r)
    }
}
