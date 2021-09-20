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

import cats.effect._
import cats.effect.std._
import cats.implicits._
import org.http4s._
import org.http4s.headers._
import org.typelevel.ci._

import java.nio.file.NoSuchFileException
import scala.annotation.nowarn

trait MultipartParserSuitePlatform { self: MultipartParserSuite =>

  {
    @nowarn("cat=deprecation")
    val testDeprecated = multipartParserTests(
      "mixed file parser",
      MultipartParser.parseStreamedFile[IO](_),
      MultipartParser.parseStreamedFile[IO](_, _),
      MultipartParser.parseToPartsStreamedFile[IO](_)
    )
    testDeprecated
  }

  multipartParserResourceTests(
    "supervised file parser",
    b => Supervisor[IO].map(MultipartParser.parseSupervisedFile[IO](_, b)),
    (b, limit) => Supervisor[IO].map(MultipartParser.parseSupervisedFile[IO](_, b, limit)),
    b => Supervisor[IO].map(MultipartParser.parseToPartsSupervisedFile[IO](_, b))
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
    @nowarn("cat=deprecation")
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
    @nowarn("cat=deprecation")
    val results = unspool(input).through(
      MultipartParser
        .parseStreamedFile[IO](boundaryTest, maxParts = 1, failOnLimit = true))

    results.compile.last
      .map(_.get)
      .intercept[MalformedMessageBodyFailure]
  }

  test("Multipart supervised file parser: truncate parts when limit set") {
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
    val mkResults =
      Supervisor[IO].map { supervisor =>
        unspool(input).through(
          MultipartParser.parseSupervisedFile[IO](supervisor, boundaryTest, maxParts = 1))
      }

    mkResults.use { results =>
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
  }

  test(
    "Multipart supervised file parser: fail parsing when parts limit exceeded if set fail as option") {
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
    val mkResults =
      Supervisor[IO].map { supervisor =>
        unspool(input).through(
          MultipartParser
            .parseSupervisedFile[IO](supervisor, boundaryTest, maxParts = 1, failOnLimit = true))
      }

    mkResults.map { results =>
      results.compile.last
        .map(_.get)
        .intercept[MalformedMessageBodyFailure]
    }
  }

  test("Multipart supervised file parser: dispose of the files when the resource is released") {
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
    val mkResults =
      Supervisor[IO].map { supervisor =>
        unspool(input).through(
          MultipartParser
            // Make sure the data will get written to files
            .parseSupervisedFile[IO](supervisor, boundaryTest, maxSizeBeforeWrite = 8))
      }

    // This is roundabout, but there's no way to test this directly without stubbing `Files` somehow.
    mkResults
      .use { results =>
        results.compile.last
          .map(_.get)
      }
      .flatMap { stale =>
        // At this point, the supervisor was released, so the files have to have been deleted.
        stale.parts.traverse_(
          _.body.compile.drain
            .intercept[NoSuchFileException]
        )
      }
  }

}
