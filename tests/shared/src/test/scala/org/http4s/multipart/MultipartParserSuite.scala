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
package multipart

import cats.effect._
import cats.effect.std._
import cats.syntax.all._
import fs2._
import fs2.io.file.NoSuchFileException
import org.http4s._
import org.http4s.headers._
import org.http4s.util._
import org.typelevel.ci._

import java.nio.charset.StandardCharsets
import scala.annotation.nowarn
import scala.annotation.tailrec

class MultipartParserSuite extends Http4sSuite {

  private val boundary = Boundary("_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI")

  private def ruinDelims(str: String) =
    augmentString(str).flatMap {
      case '\n' => "\r\n"
      case c => c.toString
    }

  def jumble(str: String): Stream[IO, Byte] = {
    val rand = new scala.util.Random()

    @tailrec
    def jumbleAccum(s: String, acc: Stream[IO, Byte]): Stream[IO, Byte] =
      if (s.length <= 1)
        acc ++ Stream.chunk(Chunk.array(s.getBytes()))
      else {
        val (l, r) = s.splitAt(rand.nextInt(s.length - 1) + 1)
        jumbleAccum(r, acc ++ Stream.chunk(Chunk.array(l.getBytes)))
      }

    jumbleAccum(str, Stream.empty)
  }

  def unspool(
      str: String,
      limit: Int = Int.MaxValue,
      charset: java.nio.charset.Charset = StandardCharsets.US_ASCII,
  ): Stream[IO, Byte] =
    if (str.isEmpty)
      Stream.empty
    else if (str.length <= limit)
      Stream.emits(str.getBytes(charset).toList)
    else {
      val (front, back) = str.splitAt(limit)
      Stream.emits(front.getBytes(charset).toList) ++ unspool(back, limit, charset)
    }

  def multipartParserTests(
      testName: String,
      multipartPipe: Boundary => Pipe[IO, Byte, Multipart[IO]],
      limitedPipe: (Boundary, Int) => Pipe[IO, Byte, Multipart[IO]],
      partsPipe: Boundary => Pipe[IO, Byte, Part[IO]],
  )(implicit loc: munit.Location): Unit =
    multipartParserResourceTests(
      testName,
      boundary => Resource.pure(multipartPipe(boundary)),
      (boundary, limit) => Resource.pure(limitedPipe(boundary, limit)),
      boundary => Resource.pure(partsPipe(boundary)),
    )

  def multipartParserResourceTests(
      testName: String,
      mkMultipartPipe: Boundary => Resource[IO, Pipe[IO, Byte, Multipart[IO]]],
      mkLimitedPipe: (Boundary, Int) => Resource[IO, Pipe[IO, Byte, Multipart[IO]]],
      mkPartsPipe: Boundary => Resource[IO, Pipe[IO, Byte, Part[IO]]],
  )(implicit loc: munit.Location): Unit = {

    val testNamePrefix = s"form streaming parsing for $testName"

    def produceBodyFromSinglePart(chunkSize: Int): Unit =
      test(s"$testNamePrefix: produce the body from a single part with chunk size $chunkSize") {
        val unprocessedInput =
          """
              |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
              |Content-Disposition: form-data; name="upload"; filename="integration.txt"
              |Content-Type: application/octet-stream
              |Content-Transfer-Encoding: binary
              |
              |this is a test
              |here's another test
              |catch me if you can!
              |
              |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI--""".stripMargin

        val input = ruinDelims(unprocessedInput)

        val expectedHeaders = Headers(
          `Content-Disposition`(
            "form-data",
            Map(ci"name" -> "upload", ci"filename" -> "integration.txt"),
          ),
          `Content-Type`(MediaType.application.`octet-stream`),
          "Content-Transfer-Encoding" -> "binary",
        )

        val expected = ruinDelims("""this is a test
              |here's another test
              |catch me if you can!
              |""".stripMargin)

        val mkResults =
          mkMultipartPipe(boundary).map(
            _(unspool(input, chunkSize))
          )

        mkResults.use { results =>
          for {
            multipartMaterialized <- results.compile.last.map(_.get)
            headers = multipartMaterialized.parts.foldLeft(Headers.empty)(_ ++ _.headers)
            bodies = multipartMaterialized.parts
              .foldLeft(Stream.empty: Stream[IO, Byte])(_ ++ _.body)
              .through(asciiDecode)
              .compile
              .foldMonoid
            result <- bodies.attempt
          } yield {
            assertEquals(headers, expectedHeaders)
            assertEquals(result, Right(expected))
          }
        }
      }

    List(1, 2, 3, 5, 8, 13, 21, 987).foreach(produceBodyFromSinglePart)

    test(s"$testNamePrefix: produce the body from a single part that doesn't start with a \r\n") {
      val unprocessedInput =
        """--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
            |Content-Disposition: form-data; name="upload"; filename="integration.txt"
            |Content-Type: application/octet-stream
            |Content-Transfer-Encoding: binary
            |
            |this is a test
            |here's another test
            |catch me if you can!
            |
            |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI--""".stripMargin

      val input = ruinDelims(unprocessedInput)
      val mkResults =
        mkMultipartPipe(boundary).map(
          _(unspool(input, 15))
        )

      val expectedHeaders = Headers(
        `Content-Disposition`(
          "form-data",
          Map(ci"name" -> "upload", ci"filename" -> "integration.txt"),
        ),
        `Content-Type`(MediaType.application.`octet-stream`),
        "Content-Transfer-Encoding" -> "binary",
      )

      val expected = ruinDelims("""this is a test
            |here's another test
            |catch me if you can!
            |""".stripMargin)

      mkResults.use { results =>
        for {
          multipartMaterialized <- results.compile.last.map(_.get)
          headers = multipartMaterialized.parts.foldLeft(Headers.empty)(_ ++ _.headers)
          bodies =
            multipartMaterialized.parts
              .foldLeft(Stream.empty: Stream[IO, Byte])(_ ++ _.body)
              .through(asciiDecode)
              .compile
              .foldMonoid
          result <- bodies.attempt
        } yield {
          assertEquals(headers, expectedHeaders)
          assertEquals(result, Right(expected))
        }
      }
    }

    test(s"$testNamePrefix: parse utf8 headers properly") {
      val unprocessedInput =
        """--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
            |Content-Disposition: form-data; name*="http4s很棒"; filename*="我老婆太漂亮.txt"
            |Content-Type: application/octet-stream
            |Content-Transfer-Encoding: binary
            |
            |this is a test
            |here's another test
            |catch me if you can!
            |
            |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI--""".stripMargin

      val input = ruinDelims(unprocessedInput)
      val mkResults =
        mkMultipartPipe(boundary).map(
          _(unspool(input, 15, StandardCharsets.UTF_8))
        )

      val expectedHeaders = Headers(
        "Content-Disposition" -> """form-data; name*="http4s很棒"; filename*="我老婆太漂亮.txt"""",
        `Content-Type`(MediaType.application.`octet-stream`),
        "Content-Transfer-Encoding" -> "binary",
      )

      val expected = ruinDelims("""this is a test
            |here's another test
            |catch me if you can!
            |""".stripMargin)

      mkResults.use { results =>
        for {
          multipartMaterialized <- results.compile.last.map(_.get)
          headers = multipartMaterialized.parts.foldLeft(Headers.empty)(_ ++ _.headers)
          bodies =
            multipartMaterialized.parts
              .foldLeft(Stream.empty: Stream[IO, Byte])(_ ++ _.body)
              .through(asciiDecode)
              .compile
              .foldMonoid
          result <- bodies.attempt
        } yield {

          assertEquals(headers, expectedHeaders)
          assertEquals(result, Right(expected))
        }
      }
    }

    test(s"$testNamePrefix: parse characterset encoded headers properly") {
      val unprocessedInput =
        """--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
            |Content-Disposition: form-data; name*=UTF-8''http4s%20withspace; filename*=UTF-8''%E6%88%91%E8%80%81%E5%A9%86%E5%A4%AA%E6%BC%82%E4%BA%AE.txt
            |Content-Type: application/octet-stream
            |Content-Transfer-Encoding: binary
            |
            |this is a test
            |here's another test
            |catch me if you can!
            |
            |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI--""".stripMargin

      val input = ruinDelims(unprocessedInput)
      val mkResults =
        mkMultipartPipe(boundary).map(
          _(unspool(input, 15, StandardCharsets.UTF_8))
        )

      val expectedHeaders = Headers(
        // #4513 for why this isn't a modeled header
        `Content-Disposition`(
          "form-data",
          Map(ci"name*" -> "http4s withspace", ci"filename*" -> "我老婆太漂亮.txt"),
        ),
        `Content-Type`(MediaType.application.`octet-stream`),
        "Content-Transfer-Encoding" -> "binary",
      )

      val expected = ruinDelims("""this is a test
            |here's another test
            |catch me if you can!
            |""".stripMargin)

      mkResults.use { results =>
        for {
          multipartMaterialized <- results.compile.last.map(_.get)
          headers = multipartMaterialized.parts.foldLeft(Headers.empty)(_ ++ _.headers)
          bodies =
            multipartMaterialized.parts
              .foldLeft(Stream.empty: Stream[IO, Byte])(_ ++ _.body)
              .through(asciiDecode)
              .compile
              .foldMonoid
          result <- bodies.attempt
        } yield {
          assertEquals(headers, expectedHeaders)
          assertEquals(result, Right(expected))
        }
      }
    }

    test(s"$testNamePrefix: discard preamble and epilogue") {
      val unprocessedInput =
        """--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
            |Content-Disposition: form-data; name="upload"; filename="integration.txt"
            |Content-Type: application/octet-stream
            |Content-Transfer-Encoding: binary
            |
            |this is a test
            |here's another test
            |catch me if you can!
            |
            |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI--""".stripMargin

      val input = ruinDelims(unprocessedInput)

      val expectedHeaders = Headers(
        `Content-Disposition`(
          "form-data",
          Map(ci"name" -> "upload", ci"filename" -> "integration.txt"),
        ),
        `Content-Type`(MediaType.application.`octet-stream`),
        "Content-Transfer-Encoding" -> "binary",
      )

      val expected = ruinDelims("""this is a test
            |here's another test
            |catch me if you can!
            |""".stripMargin)

      val preamble: Stream[IO, Byte] =
        Stream
          .constant("Misery is the river of the world")
          .take(10)
          .through(text.utf8.encode)

      val crlf: Stream[IO, Byte] =
        Stream
          .emit(Boundary.CRLF)
          .through(text.utf8.encode)

      val epilogue: Stream[IO, Byte] =
        Stream
          .constant("Everybody Row!\n")
          .take(10)
          .through(text.utf8.encode)

      val mkResults =
        mkMultipartPipe(boundary).map(
          _(
            preamble ++
              crlf ++
              unspool(input, 15) ++
              epilogue
          )
        )

      mkResults.use { results =>
        for {
          multipartMaterialized <- results.compile.last.map(_.get)
          headers = multipartMaterialized.parts.foldLeft(Headers.empty)(_ ++ _.headers)
          bodies = multipartMaterialized.parts
            .foldLeft(Stream.empty: Stream[IO, Byte])(_ ++ _.body)
            .through(asciiDecode)
            .compile
            .foldMonoid
          result <- bodies.attempt
        } yield {
          assertEquals(headers, expectedHeaders)
          assertEquals(result, Right(expected))
        }
      }
    }

    test(s"$testNamePrefix: fail if the header is too large") {
      // This is a valid multipart body, but in this example, we're imposing an
      // absurdly low cap in the argument to MultipartParser.parse to trigger failure.
      val unprocessedInput =
        """
          |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
          |Content-Disposition: form-data; name="upload"; filename="integration.txt"
          |Content-Type: application/octet-stream
          |Content-Transfer-Encoding: binary
          |
          |this is a test
          |here's another test
          |catch me if you can!
          |
          |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI--""".stripMargin
      val input = ruinDelims(unprocessedInput)

      val headerSection =
        """
            |Content-Disposition: form-data; name="upload"; filename="integration.txt"
            |Content-Type: application/octet-stream
            |Content-Transfer-Encoding: binary""".stripMargin
      val maxSize = ruinDelims(headerSection).length

      val mkResults =
        mkLimitedPipe(boundary, maxSize).map(
          _(unspool(input, 15))
        )

      mkResults.use { results =>
        results.compile.toVector
          .interceptMessage[MalformedMessageBodyFailure](
            s"Malformed message body: Part header was longer than $maxSize-byte limit"
          )
      }
    }

    test(s"$testNamePrefix: handle a miserably large body on one line") {
      val input =
        ruinDelims("""--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
              |Content-Disposition: form-data; name="upload"; filename="integration.txt"
              |Content-Type: application/octet-stream
              |Content-Transfer-Encoding: binary
              |
        """.stripMargin)
      val end = "--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI--"

      val expectedHeaders = Headers(
        `Content-Disposition`(
          "form-data",
          Map(ci"name" -> "upload", ci"filename" -> "integration.txt"),
        ),
        `Content-Type`(MediaType.application.`octet-stream`),
        "Content-Transfer-Encoding" -> "binary",
      )

      val crlf: Stream[IO, Byte] =
        Stream
          .emit(Boundary.CRLF)
          .through(text.utf8.encode)

      val body: Stream[IO, Byte] = Stream
        .constant("Misery is the river of the world")
        .take(100000)
        .through(text.utf8.encode)

      val mkResults = mkMultipartPipe(boundary).map(
        _(
          unspool(input) ++
            body ++
            crlf ++
            unspool(end)
        )
      )

      mkResults.use { results =>
        results.compile.last
          .map(_.get)
          .map(_.parts.foldLeft(Headers.empty)(_ ++ _.headers))
          .assertEquals(expectedHeaders)
      }
    }

    test(s"$testNamePrefix: produce the body from a single part input of one chunk") {
      val unprocessedInput =
        """
            |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
            |Content-Disposition: form-data; name="upload"; filename="integration.txt"
            |Content-Type: application/octet-stream
            |Content-Transfer-Encoding: binary
            |
            |this is a test
            |here's another test
            |catch me if you can!
            |
            |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI--""".stripMargin

      val input = ruinDelims(unprocessedInput)

      val expectedHeaders = Headers(
        `Content-Disposition`(
          "form-data",
          Map(ci"name" -> "upload", ci"filename" -> "integration.txt"),
        ),
        `Content-Type`(MediaType.application.`octet-stream`),
        "Content-Transfer-Encoding" -> "binary",
      )

      val expected = ruinDelims("""this is a test
            |here's another test
            |catch me if you can!
            |""".stripMargin)

      val mkResults =
        mkMultipartPipe(boundary).map(
          _(unspool(input))
        )

      mkResults.use { results =>
        for {
          multipartMaterialized <- results.compile.last.map(_.get)
          headers = multipartMaterialized.parts.foldLeft(Headers.empty)(_ ++ _.headers)
          bodies = multipartMaterialized.parts
            .foldLeft(Stream.empty: Stream[IO, Byte])(_ ++ _.body)
            .through(asciiDecode)
            .compile
            .foldMonoid
          result <- bodies.attempt
        } yield {
          assertEquals(headers, expectedHeaders)
          assertEquals(result, Right(expected))
        }
      }
    }

    test(s"$testNamePrefix: produce the body from a two-part input") {
      val unprocessedInput =
        """
            |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
            |Content-Disposition: form-data; name="upload"; filename="integration.txt"
            |Content-Type: application/octet-stream
            |Content-Transfer-Encoding: binary
            |
            |this is a test
            |here's another test
            |catch me if you can!
            |
            |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
            |Content-Disposition: form-data; name="foo"
            |
            |bar
            |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI--""".stripMargin

      val input = ruinDelims(unprocessedInput)

      val mkResults =
        mkMultipartPipe(boundary).map(
          _(unspool(input))
        )

      mkResults.use { results =>
        results.compile.last
          .map(_.get)
          .map(
            _.parts(1).body
              .through(asciiDecode)
              .compile
              .foldMonoid
          )
          .flatMap(_.attempt)
          .assertEquals(Right("bar"))
      }
    }

    test(s"$testNamePrefix: parse uneven input properly") {
      val unprocessed =
        Stream
          .emits(
            List(
              "--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI\n",
              "Content-Disposition: form-data; name=\"upload\"; filename=\"integration.txt\"\n",
              """Content-Type: application/octet-stream
                  |Content-Transfer-Encoding: binary
                  |
                  |this is a test
                  |here's another test
                  |catch me if you can!
                  |
                  |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
                  |Content-Disposition: form-data; name="foo"
                  |
                  |""".stripMargin,
              """bar
                  |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI--""".stripMargin,
            ).map(_.replace("\n", "\r\n"))
              .map(str => Chunk.array(str.getBytes(StandardCharsets.UTF_8)))
          )
          .flatMap(Stream.chunk)

      val mkResults =
        mkMultipartPipe(boundary).map(
          _(unprocessed)
        )

      mkResults.use { results =>
        results.compile.last
          .map(_.get)
          .map(
            _.parts(1).body
              .through(asciiDecode)
              .compile
              .foldMonoid
          )
          .flatMap(_.attempt)
          .assertEquals(Right("bar"))
      }
    }

    def parseRandomizedChunkLength(count: Int): Unit =
      test(s"$testNamePrefix: parse randomized chunk length properly iteration #$count") {
        val unprocessedInput =
          """
              |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
              |Content-Disposition: form-data; name="upload"; filename="integration.txt"
              |Content-Type: application/octet-stream
              |Content-Transfer-Encoding: binary
              |
              |this is a test
              |here's another test
              |catch me if you can!
              |
              |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
              |Content-Disposition: form-data; name="foo"
              |
              |bar
              |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI--""".stripMargin.replace("\n", "\r\n")

        val unprocessed = jumble(unprocessedInput)

        val mkResults =
          mkMultipartPipe(boundary).map(
            _(unprocessed)
          )

        mkResults.use { results =>
          results.compile.last
            .map(_.get)
            .map(
              _.parts(1).body
                .through(asciiDecode)
                .compile
                .foldMonoid
            )
            .flatMap(_.attempt)
            .assertEquals(Right("bar"))
        }
      }

    List.range(0, 100).foreach(parseRandomizedChunkLength)

    test(s"$testNamePrefix: produce the correct headers from a two part input") {
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
        mkMultipartPipe(boundaryTest).map(
          _(unspool(input))
        )

      mkResults.use { results =>
        results.compile.last
          .map(_.get)
          .map(_.parts.foldLeft(List.empty[Headers])((l, r) => l ::: List(r.headers)))
          .assertEquals(
            List(
              Headers(
                `Content-Disposition`("form-data", Map(ci"name" -> "field1")),
                `Content-Type`(MediaType.text.plain),
              ),
              Headers(
                `Content-Disposition`("form-data", Map(ci"name" -> "field2"))
              ),
            )
          )
      }
    }

    test(s"$testNamePrefix: parse parts lazily") {
      // Intentionally mangle the end, which would fail if we consume the whole thing,
      // but not if we only take one part, as each part should parse lazily
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
          |--RU(_9F(PcJK5+JMOPCAF6Aj4iSXvpJkWy):6""".stripMargin

      val input = ruinDelims(unprocessedInput)

      val boundaryTest = Boundary("RU(_9F(PcJK5+JMOPCAF6Aj4iSXvpJkWy):6s)YU0")
      val mkResults =
        mkPartsPipe(boundaryTest).map(
          _(unspool(input))
        )

      mkResults.use { results =>
        for {
          firstPart <- results.take(1).compile.last.map(_.get)
          confirmedError <- results.compile.drain.attempt
          _ <- firstPart.body
            .through(text.utf8.decode[IO])
            .compile
            .foldMonoid
        } yield {
          assertEquals(
            firstPart.headers,
            Headers(
              `Content-Disposition`("form-data", Map(ci"name" -> "field1")),
              `Content-Type`(MediaType.text.plain),
            ),
          )
          assert(confirmedError.isInstanceOf[Left[_, _]])
          assert(
            confirmedError.left
              .getOrElse(throw new Exception)
              .isInstanceOf[MalformedMessageBodyFailure]
          )
        }
      }
    }

    def drainEpilogue(chunkSize: Int): Unit =
      test(s"$testNamePrefix: drain the epilogue with chunk size $chunkSize") {
        val unprocessedInput =
          """
            |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
            |Content-Disposition: form-data; name="foo"
            |
            |bar
            |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI--
            |This should be ignored, but still drained!""".stripMargin

        val input = ruinDelims(unprocessedInput)

        mkMultipartPipe(boundary).use { multipartPipe =>
          for {
            // This should be false until we drain the whole input.
            ref <- Ref[IO].of(false)
            trackedInput = unspool(input, chunkSize) ++ Stream.eval(ref.set(true)).drain

            _ <- trackedInput.through(multipartPipe).compile.drain

            reachedTheEnd <- ref.get
          } yield assert(reachedTheEnd)
        }
      }

    List(1, 2, 3, 5, 8, 13, 21, 987).foreach(drainEpilogue)

    test(s"$testNamePrefix: fail with an MalformedMessageBodyFailure without an end line") {
      val unprocessedInput =
        """
            |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
            |Content-Disposition: form-data; name="upload"; filename="integration.txt"
            |Content-Type: application/octet-stream
            |Content-Transfer-Encoding: binary
            |
            |this is a test
            |here's another test
            |catch me if you can!""".stripMargin
      val input = ruinDelims(unprocessedInput)

      val mkResults =
        mkMultipartPipe(boundary).map(
          _(unspool(input))
        )

      mkResults.use { results =>
        results.compile.toVector.intercept[MalformedMessageBodyFailure]
      }
    }
  }

  multipartParserTests(
    "Default parser",
    MultipartParser.parseStreamed[IO](_),
    MultipartParser.parseStreamed[IO],
    MultipartParser.parseToPartsStream[IO](_),
  )

  {
    @nowarn("cat=deprecation")
    val testDeprecated = multipartParserTests(
      "mixed file parser",
      MultipartParser.parseStreamedFile[IO](_),
      MultipartParser.parseStreamedFile[IO](_, _),
      MultipartParser.parseToPartsStreamedFile[IO](_),
    )
    testDeprecated
  }

  multipartParserResourceTests(
    "supervised file parser",
    b => Supervisor[IO].map(MultipartParser.parseSupervisedFile[IO](_, b)),
    (b, limit) => Supervisor[IO].map(MultipartParser.parseSupervisedFile[IO](_, b, limit)),
    b => Supervisor[IO].map(MultipartParser.parseToPartsSupervisedFile[IO](_, b)),
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
            `Content-Type`(MediaType.text.plain),
          )
        )
      )
  }

  test(
    "Multipart mixed file parser: fail parsing when parts limit exceeded if set fail as option"
  ) {
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
        .parseStreamedFile[IO](boundaryTest, maxParts = 1, failOnLimit = true)
    )

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
          MultipartParser.parseSupervisedFile[IO](supervisor, boundaryTest, maxParts = 1)
        )
      }

    mkResults.use { results =>
      results.compile.last
        .map(_.get)
        .map(_.parts.foldLeft(List.empty[Headers])((l, r) => l ::: List(r.headers)))
        .assertEquals(
          List(
            Headers(
              `Content-Disposition`("form-data", Map(ci"name" -> "field1")),
              `Content-Type`(MediaType.text.plain),
            )
          )
        )
    }
  }

  test(
    "Multipart supervised file parser: fail parsing when parts limit exceeded if set fail as option"
  ) {
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
            .parseSupervisedFile[IO](supervisor, boundaryTest, maxParts = 1, failOnLimit = true)
        )
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
            .parseSupervisedFile[IO](supervisor, boundaryTest, maxSizeBeforeWrite = 8)
        )
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
