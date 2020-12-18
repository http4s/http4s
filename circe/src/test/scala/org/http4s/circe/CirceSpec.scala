/*
 * Copyright 2015 http4s.org
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
package circe.test // Get out of circe package so we can import custom instances

import cats.effect.IO
import cats.effect.laws.util.TestContext
import cats.effect.laws.util.TestInstances._
import io.circe._
import io.circe.testing.instances._
import org.http4s.circe._
import org.http4s.laws.discipline.EntityCodecTests

// Originally based on ArgonautSuite
class CirceSpec extends Http4sSpec {
  implicit val testContext = TestContext()
  checkAll("EntityCodec[IO, Json]", EntityCodecTests[IO, Json].entityCodec)
}
