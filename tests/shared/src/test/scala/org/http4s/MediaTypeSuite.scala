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

import cats.kernel.laws.discipline.EqTests
import cats.syntax.show._
import org.http4s.laws.discipline.HttpCodecTests
import org.http4s.laws.discipline.arbitrary._

class MediaTypeSuite extends Http4sSuite {
  checkAll("Eq[MediaType]", EqTests[MediaType].eqv)
  checkAll("HttpCodec[MediaType]", HttpCodecTests[MediaType].httpCodec)

  test("MediaType should Render itself") {
    assertEquals(MediaType.text.html.show, "text/html")
  }

  test("MediaType should Quote extension strings") {
    assertEquals(
      MediaType.text.html
        .withExtensions(Map("foo" -> "bar"))
        .show,
      """text/html; foo="bar"""",
    )
  }

  test("MediaType should Encode extensions with special characters") {
    assertEquals(
      MediaType.text.html
        .withExtensions(Map("foo" -> ";"))
        .show,
      """text/html; foo=";"""",
    )
  }

  test("MediaType should Escape special chars in media range extensions") {
    assertEquals(
      MediaType.text.html
        .withExtensions(Map("foo" -> "\\"))
        .show,
      """text/html; foo="\\"""",
    )
    assertEquals(
      MediaType.text.html
        .withExtensions(Map("foo" -> "\""))
        .show,
      """text/html; foo="\""""",
    )
  }

  test("MediaType should reject invalid literals") {
    assert(
      compileErrors {
        """mediaType"not valid""""
      }.nonEmpty
    )
  }

}
