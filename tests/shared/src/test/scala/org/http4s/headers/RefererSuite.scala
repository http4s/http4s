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
package headers

import cats.effect.IO
import org.http4s.laws.discipline.arbitrary._
import org.http4s.syntax.header._

class RefererSuite extends HeaderLaws {
  checkAll("Referer", headerLaws[`Retry-After`])

  def getUri(uri: String): Uri =
    Uri.fromString(uri).fold(_ => sys.error(s"Failure on uri: $uri"), identity)

  test("render format an absolute url") {
    assertEquals(
      Referer(getUri("http://localhost:8080")).renderString,
      "Referer: http://localhost:8080",
    )
  }
  test("render format a relative url") {
    assertEquals(Referer(getUri("../../index.html")).renderString, "Referer: ../../index.html")
  }

  test("parser should accept absolute url") {
    assertEquals(
      Referer.parse("http://localhost:8080").map(_.uri),
      Right(getUri("http://localhost:8080")),
    )
  }
  test("parser should accept relative url") {
    assertEquals(Referer.parse("../../index.html").map(_.uri), Right(getUri("../../index.html")))
  }

  test("should be extractable") {
    val referer = Referer(getUri("http://localhost:8080"))
    val request = Request[IO](headers = Headers(referer))

    val extracted = request.headers.get[Referer]
    assertEquals(extracted, Some(referer))
  }
}
