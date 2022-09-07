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

import org.http4s.headers.`Content-Type`
import org.http4s.syntax.header._

class ContentTypeHeaderSuite extends Http4sSuite {
  def parse(value: String): ParseResult[`Content-Type`] =
    `Content-Type`.parse(value)

  private def simple = `Content-Type`(MediaType.text.html)
  private def charset = `Content-Type`(MediaType.text.html, Charset.`UTF-8`)
  private def extensions = `Content-Type`(MediaType.text.html.withExtensions(Map("foo" -> "bar")))
  private def extensionsandset =
    `Content-Type`(MediaType.text.html.withExtensions(Map("foo" -> "bar")), Charset.`UTF-8`)
  private def multipart =
    `Content-Type`(
      MediaType.multipart.`form-data`.withExtensions(Map("boundary" -> "aLotOfMoose")),
      Charset.`UTF-8`,
    )

  {
    test("ContentType Header should Generate the correct values") {
      assertEquals(simple.value, "text/html")
      assertEquals(charset.value, """text/html; charset=UTF-8""")
      assertEquals(extensions.value, """text/html; foo="bar"""")
      assertEquals(extensionsandset.value, """text/html; foo="bar"; charset=UTF-8""")
      assertEquals(
        multipart.value,
        """multipart/form-data; boundary="aLotOfMoose"; charset=UTF-8""",
      )
    }

    test("ContentType Header should Parse correctly") {
      assertEquals(parse(simple.value), Right(simple))
      assertEquals(parse(charset.value), Right(charset))
      assertEquals(parse(extensions.value), Right(extensions))
      assertEquals(parse(extensionsandset.value), Right(extensionsandset))
      assertEquals(parse(multipart.value), Right(multipart))
    }
  }
}
