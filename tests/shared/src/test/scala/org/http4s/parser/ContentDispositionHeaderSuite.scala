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
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalacheck.Prop
import org.typelevel.ci._

class ContentDispositionHeaderSuite extends Http4sSuite {
  def parse(value: String): ParseResult[`Content-Disposition`] =
    `Content-Disposition`.parse(value)

  test("ContentDisposition Header should render the correct values") {

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

  test("ContentDisposition Header should parse the correct values") {
    val utf8Encoded =
      """form-data; filename="http4s ??"; filename*=UTF-8''http4s%20%C5%82%C5%82"""
    val pureEncoded = """form-data; filename="some value""""

    assertEquals(
      parse(utf8Encoded),
      Right(
        `Content-Disposition`(
          "form-data",
          Map(ci"filename" -> "http4s ??", ci"filename*" -> "http4s łł"),
        )
      ),
    )

    assertEquals(
      parse(pureEncoded),
      Right(
        `Content-Disposition`("form-data", Map(ci"filename" -> "some value"))
      ),
    )
  }

  property("ContentDisposition filename encoding roundtrip") {
    // To be sure both kind of string will be checked
    Prop.forAll(
      Gen.stringOf(Gen.oneOf(arbitrary[Char], Gen.asciiChar).suchThat(_ != '\u0000'))
    ) { filename =>
      val header = `Content-Disposition`(
        "form-data",
        Map(ci"filename" -> filename),
      )

      def check(actual0: `Content-Disposition`): Prop = {
        val actual = actual0.parameters
          .get(ci"filename*")
          .orElse(actual0.parameters.get(ci"filename"))
          .getOrElse("")

        assertEquals(actual, filename)
      }

      parse(header.value).fold[Prop](
        e => Prop(false) :| e.toString,
        check,
      )
    }
  }

  test("Content-Disposition header should pick 'filename*' parameter and ignore 'filename'") {
    val headerValue = """attachment; filename="http4s"; filename*=UTF-8''http4s%20%F0%9F%98%8A"""
    val parsedHeader = `Content-Disposition`.parse(headerValue)
    val header = `Content-Disposition`(
      "attachment",
      Map(ci"filename" -> "http4s", ci"filename*" -> "http4s 😊"),
    )
    assertEquals(parsedHeader, Right(header))
  }

  test("filename property should prefer `filename*` over `filename` parameter") {
    val headerValue = """attachment; filename="http4s"; filename*=UTF-8''http4s%20%F0%9F%98%8A"""
    val parsedHeader = `Content-Disposition`.parse(headerValue)
    assertEquals(parsedHeader.map(_.filename), Right(Some("http4s 😊")))
  }

  test("filename property should fall back to `filename` in absence of `filename*` parameter") {
    val headerValue = """attachment; filename="http4s""""
    val parsedHeader = `Content-Disposition`.parse(headerValue)
    assertEquals(parsedHeader.map(_.filename), Right(Some("http4s")))
  }

  test("filename property should be none in absence of `filename` and `filename*` parameters") {
    val headerValue = """attachment"""
    val parsedHeader = `Content-Disposition`.parse(headerValue)
    assertEquals(parsedHeader.map(_.filename), Right(None))
  }
}
