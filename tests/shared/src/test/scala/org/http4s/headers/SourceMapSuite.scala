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
import org.http4s.syntax.all._

class SourceMapSuite extends HeaderLaws {

  test("render format an absolute url") {
    assertEquals(
      SourceMap(uri"http://localhost:8080/index.js.map").renderString,
      "SourceMap: http://localhost:8080/index.js.map",
    )
  }

  test("render format a relative url up") {
    assertEquals(
      SourceMap(uri"../../index.js.map").renderString,
      "SourceMap: ../../index.js.map",
    )
  }

  test("render format a relative url") {
    assertEquals(SourceMap(uri"/index.js.map").renderString, "SourceMap: /index.js.map")
  }

  test("parser should accept absolute url") {
    assertEquals(
      SourceMap.parse("http://localhost:8080/index.js.map").map(_.uri),
      Right(uri"http://localhost:8080/index.js.map"),
    )
  }

  test("parser should accept relative url up") {
    assertEquals(
      SourceMap.parse("../../index.js.map").map(_.uri),
      Right(uri"../../index.js.map"),
    )
  }

  test("parser should accept relative url") {
    assertEquals(
      SourceMap.parse("/index.js.map").map(_.uri),
      Right(uri"/index.js.map"),
    )
  }

  test("should be extractable") {
    val sourceMap = SourceMap(uri"http://localhost:8080/index.js.map")
    val request = Request[IO](headers = Headers(sourceMap))

    val extracted = request.headers.get[SourceMap]
    assertEquals(extracted, Some(sourceMap))
  }
}
