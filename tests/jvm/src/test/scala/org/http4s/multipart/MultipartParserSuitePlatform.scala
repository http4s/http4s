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

package org.http4s.multipart

import cats.effect.IO
import org.http4s._
import org.http4s.headers._
import org.typelevel.ci._

trait MultipartParserSuitePlatform { self: MultipartParserSuite =>

  multipartParserTests(
    "mixed file parser",
    MultipartParser.parseStreamedFile[IO](_),
    MultipartParser.parseStreamedFile[IO](_, _),
    MultipartParser.parseToPartsStreamedFile[IO](_)
  )

  test("Multipart mixed file parser: truncate parts when limit set") {
    val unprocessedInput =
      """
          |--RU(_9F(PcJK5+JMOPCAF6Aj4iSXvpJkWy):6s)YU0
          |Content-Disposition: form-data; name="field1"
          |Content-Type: text/plain
          |
          |Text_Field_1
          |--RU(_9F(PcJK5+JMOPCAF6Aj4iSXvpJkWy):6s)YU0
          |Content-Disposition: form-data; name="field2"
          |
          |Text_Field_2
          |--RU(_9F(PcJK5+JMOPCAF6Aj4iSXvpJkWy):6s)YU0--""".stripMargin

    val input = ruinDelims(unprocessedInput)

    val boundaryTest = Boundary("RU(_9F(PcJK5+JMOPCAF6Aj4iSXvpJkWy):6s)YU0")
    val results =
      unspool(input).through(MultipartParser.parseStreamedFile[IO](boundaryTest, maxParts = 1))

    results.compile.last
      .map(_.get)
      .map(_.parts.foldLeft(List.empty[Headers])((l, r) => l ::: List(r.headers)))
      .assertEquals(
        List(
          Headers(
            `Content-Disposition`("form-data", Map(ci"name" -> "field1")),
            `Content-Type`(MediaType.text.plain)
          )
        ))
  }

  test(
    "Multipart mixed file parser: fail parsing when parts limit exceeded if set fail as option") {
    val unprocessedInput =
      """
          |--RU(_9F(PcJK5+JMOPCAF6Aj4iSXvpJkWy):6s)YU0
          |Content-Disposition: form-data; name="field1"
          |Content-Type: text/plain
          |
          |Text_Field_1
          |--RU(_9F(PcJK5+JMOPCAF6Aj4iSXvpJkWy):6s)YU0
          |Content-Disposition: form-data; name="field2"
          |
          |Text_Field_2
          |--RU(_9F(PcJK5+JMOPCAF6Aj4iSXvpJkWy):6s)YU0--""".stripMargin

    val input = ruinDelims(unprocessedInput)

    val boundaryTest = Boundary("RU(_9F(PcJK5+JMOPCAF6Aj4iSXvpJkWy):6s)YU0")
    val results = unspool(input).through(
      MultipartParser
        .parseStreamedFile[IO](boundaryTest, maxParts = 1, failOnLimit = true))

    results.compile.last
      .map(_.get)
      .intercept[MalformedMessageBodyFailure]
  }

}
