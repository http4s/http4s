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

package org.http4s.headers

import org.http4s.Method
import org.http4s.laws.discipline.arbitrary._

class AllowSuite extends HeaderLaws {
  checkAll("Allow", headerLaws[Allow])

  test("Allow should parse an empty string") {
    assertEquals(Allow.parse(""), Right(Allow()))
  }

  test("Allow should parse with an ending comma") {
    assertEquals(Allow.parse("GET,  POST   ,"), Right(Allow(Method.GET, Method.POST)))
  }

  test("Allow should parse with multiple commas") {
    assertEquals(Allow.parse("GET,,POST"), Right(Allow(Method.GET, Method.POST)))
  }
}
