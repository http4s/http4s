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
package parser

import org.http4s.headers.`Content-Disposition`
import org.http4s.syntax.header._
import org.typelevel.ci._

class ContentDispositionHeaderSuite extends Http4sSuite {
  test("Content-Disposition header should render the correct values") {
    val wrongEncoding = `Content-Disposition`("form-data", Map(ci"filename" -> "http4s łł"))
    val correctOrder =
      `Content-Disposition`("form-data", Map(ci"filename*" -> "value1", ci"filename" -> "value2"))

    assertEquals(
      wrongEncoding.renderString,
      """Content-Disposition: form-data; filename="http4s ??"; filename*=UTF-8''http4s%20%C5%82%C5%82""",
    )
    assertEquals(
      correctOrder.renderString,
      """Content-Disposition: form-data; filename="value2"; filename*=UTF-8''value1""",
    )
  }

  test("Content-Disposition header should pick 'filename*' parameter and ignore 'filename'") {
    val headerValue = """form-data; filename="filename"; filename*=UTF-8''http4s%20filename"""
    val parsedHeader = `Content-Disposition`.parse(headerValue)
    val header = `Content-Disposition`("form-data", Map(CIString("filename") -> "http4s filename"))

    assertEquals(parsedHeader, Right(header))
  }
}
