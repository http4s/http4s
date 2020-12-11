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

import cats.effect.IO
import cats.effect.testing.specs2.CatsEffect

class HttpDateSpec extends Http4sSpec with CatsEffect {
  "HttpDate" should {
    "current should be extremely close to Instant.now" >> {
      HttpDate.current[IO].map { current =>
        val diff =
          HttpDate.unsafeFromInstant(java.time.Instant.now).epochSecond - current.epochSecond
        (diff must be_===(0L)).or(be_===(1L))
      }
    }
  }
}
