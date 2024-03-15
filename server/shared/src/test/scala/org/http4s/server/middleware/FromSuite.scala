/*
 * Copyright 2014 http4s.org
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

package org.http4s.server.middleware

import org.http4s._
import org.http4s.headers.From

class FromSuite extends Http4sSuite {
  test("should parse the email address") {
    val cases = List(
      ("a@example.com", "a@example.com"),
      ("a.b@example.com", "a.b@example.com"),
      ("display name <a@b.com>", "a@b.com"),
      ("\"abc!\"@example.com", "abc!@example.com"),
      ("(comment A) \r\n (comment B) a@b.com", "a@b.com"),
    )
    cases.foreach { c =>
      val result = From.parse(c._1)
      assertEquals(result.toOption.get.email, c._2)
    }

    val failCases = List(
      "a.b.com",
      "display name a@b.com",
    )
    failCases.foreach { c =>
      val result = From.parse(c)
      assert(result.isLeft)
    }
  }
}
