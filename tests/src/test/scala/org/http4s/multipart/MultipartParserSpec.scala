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

import java.nio.charset.StandardCharsets
import cats.effect._
import cats.effect.unsafe.IORuntime
import cats.instances.string._
import fs2._
import org.http4s.headers._
import org.http4s.util._
import org.specs2.mutable._
import org.specs2.specification.core.{Fragment, Fragments}

object MultipartParserSpec extends Specification {
  private implicit val ioRuntime: IORuntime = Http4sSpec.TestIORuntime

  val boundary = Boundary("_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI")

  def ruinDelims(str: String) =
    augmentString(str).flatMap {
      case '\n' => "\r\n"
      case c => c.toString
    }

  def jumble(str: String): Stream[IO, Byte] = {
    val rand = new scala.util.Random()

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
      charset: java.nio.charset.Charset = StandardCharsets.US_ASCII): Stream[IO, Byte] =
    if (str.isEmpty)
      Stream.empty
    else if (str.length <= limit)
      Stream.emits(str.getBytes(charset).toSeq)
    else {
      val (front, back) = str.splitAt(limit)
      Stream.emits(front.getBytes(charset).toSeq) ++ unspool(back, limit, charset)
    }

  def multipartParserTests(
      testName: String,
      multipartPipe: Boundary => Pipe[IO, Byte, Multipart[IO]],
      limitedPipe: (Boundary, Int) => Pipe[IO, Byte, Multipart[IO]],
      partsPipe: Boundary => Pipe[IO, Byte, Part[IO]]): Fragment = {
    s"form streaming parsing for $testName" should {
      Fragments.foreach(List(1, 2, 3, 5, 8, 13, 21, 987)) { chunkSize =>
        s"produce the body from a single part with chunk size ${chunkSize}" in {
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

          val expectedHeaders = Headers.of(
            `Content-Disposition`(
              "form-data",
              Map("name" -> "upload", "filename" -> "integration.txt")),
            `Content-Type`(MediaType.application.`octet-stream`),
            Header("Content-Transfer-Encoding", "binary")
          )

          val expected = ruinDelims("""this is a test
              |here's another test
              |catch me if you can!
              |""".stripMargin)

          val results =
            unspool(input, chunkSize).through(multipartPipe(boundary))

          val multipartMaterialized = results.compile.last.map(_.get).unsafeRunSync()
          val headers = multipartMaterialized.parts.foldLeft(Headers.empty)(_ ++ _.headers)
          val bodies = multipartMaterialized.parts
            .foldLeft(Stream.empty.covary[IO]: Stream[IO, Byte])(_ ++ _.body)
            .through(asciiDecode)
            .compile
            .foldMonoid

          headers mustEqual expectedHeaders
          bodies.attempt.unsafeRunSync() must beRight(expected)
        }
      }

      "produce the body from a single part that doesn't start with a \r\n" in {
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
        val results =
          unspool(input, 15).through(multipartPipe(boundary))
        val multipartMaterialized = results.compile.last.map(_.get).unsafeRunSync()

        val expectedHeaders = Headers.of(
          `Content-Disposition`(
            "form-data",
            Map("name" -> "upload", "filename" -> "integration.txt")),
          `Content-Type`(MediaType.application.`octet-stream`),
          Header("Content-Transfer-Encoding", "binary")
        )

        val expected = ruinDelims("""this is a test
            |here's another test
            |catch me if you can!
            |""".stripMargin)

        val headers = multipartMaterialized.parts.foldLeft(Headers.empty)(_ ++ _.headers)
        val bodies =
          multipartMaterialized.parts
            .foldLeft(Stream.empty.covary[IO]: Stream[IO, Byte])(_ ++ _.body)
            .through(asciiDecode)
            .compile
            .foldMonoid

        headers mustEqual expectedHeaders
        bodies.attempt.unsafeRunSync() must beRight(expected)
      }

      "parse utf8 headers properly" in {
        val unprocessedInput =
          """--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
            |Content-Disposition: form-data; name="http4s很棒"; filename="我老婆太漂亮.txt"
            |Content-Type: application/octet-stream
            |Content-Transfer-Encoding: binary
            |
            |this is a test
            |here's another test
            |catch me if you can!
            |
            |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI--""".stripMargin

        val input = ruinDelims(unprocessedInput)
        val results =
          unspool(input, 15, StandardCharsets.UTF_8)
            .through(multipartPipe(boundary))
        val multipartMaterialized = results.compile.last.map(_.get).unsafeRunSync()

        val expectedHeaders = Headers.of(
          `Content-Disposition`("form-data", Map("name" -> "http4s很棒", "filename" -> "我老婆太漂亮.txt")),
          `Content-Type`(MediaType.application.`octet-stream`),
          Header("Content-Transfer-Encoding", "binary")
        )

        val expected = ruinDelims("""this is a test
            |here's another test
            |catch me if you can!
            |""".stripMargin)

        val headers = multipartMaterialized.parts.foldLeft(Headers.empty)(_ ++ _.headers)
        val bodies =
          multipartMaterialized.parts
            .foldLeft(Stream.empty.covary[IO]: Stream[IO, Byte])(_ ++ _.body)
            .through(asciiDecode)
            .compile
            .foldMonoid

        headers mustEqual expectedHeaders
        bodies.attempt.unsafeRunSync() must beRight(expected)
      }

      "discard preamble and epilogue" in {
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

        val expectedHeaders = Headers.of(
          `Content-Disposition`(
            "form-data",
            Map("name" -> "upload", "filename" -> "integration.txt")),
          `Content-Type`(MediaType.application.`octet-stream`),
          Header("Content-Transfer-Encoding", "binary")
        )

        val expected = ruinDelims("""this is a test
            |here's another test
            |catch me if you can!
            |""".stripMargin)

        val preamble: Stream[IO, Byte] =
          Stream
            .constant("Misery is the river of the world")
            .take(10)
            .through(text.utf8Encode)

        val crlf: Stream[IO, Byte] =
          Stream
            .emit(Boundary.CRLF)
            .through(text.utf8Encode)

        val epilogue: Stream[IO, Byte] =
          Stream
            .constant("Everybody Row!\n")
            .take(10)
            .through(text.utf8Encode)

        val results = (
          preamble ++
            crlf ++
            unspool(input, 15) ++
            epilogue
        ).through(multipartPipe(boundary))

        val multipartMaterialized = results.compile.last.map(_.get).unsafeRunSync()
        val headers = multipartMaterialized.parts.foldLeft(Headers.empty)(_ ++ _.headers)
        val bodies = multipartMaterialized.parts
          .foldLeft(Stream.empty.covary[IO]: Stream[IO, Byte])(_ ++ _.body)
          .through(asciiDecode)
          .compile
          .foldMonoid

        headers mustEqual expectedHeaders
        bodies.attempt.unsafeRunSync() must beRight(expected)
      }

      "fail if the header is too large" in {
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

        val results =
          unspool(input, 15).through(limitedPipe(boundary, maxSize))

        results.compile.toVector.unsafeRunSync() must throwA(
          MalformedMessageBodyFailure(s"Part header was longer than $maxSize-byte limit"))
      }

      "handle a miserably large body on one line" in {
        val input =
          ruinDelims("""--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
              |Content-Disposition: form-data; name="upload"; filename="integration.txt"
              |Content-Type: application/octet-stream
              |Content-Transfer-Encoding: binary
              |
        """.stripMargin)
        val end = "--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI--"

        val expectedHeaders = Headers.of(
          `Content-Disposition`(
            "form-data",
            Map("name" -> "upload", "filename" -> "integration.txt")),
          `Content-Type`(MediaType.application.`octet-stream`),
          Header("Content-Transfer-Encoding", "binary")
        )

        val crlf: Stream[IO, Byte] =
          Stream
            .emit(Boundary.CRLF)
            .through(text.utf8Encode)

        val body: Stream[IO, Byte] = Stream
          .constant("Misery is the river of the world")
          .take(100000)
          .through(text.utf8Encode)

        val results = (
          unspool(input) ++
            body ++
            crlf ++
            unspool(end)
        ).through(multipartPipe(boundary))

        val multipartMaterialized = results.compile.last.map(_.get).unsafeRunSync()
        val headers = multipartMaterialized.parts.foldLeft(Headers.empty)(_ ++ _.headers)
        headers mustEqual expectedHeaders
      }

      "produce the body from a single part input of one chunk" in {
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

        val expectedHeaders = Headers.of(
          `Content-Disposition`(
            "form-data",
            Map("name" -> "upload", "filename" -> "integration.txt")),
          `Content-Type`(MediaType.application.`octet-stream`),
          Header("Content-Transfer-Encoding", "binary")
        )

        val expected = ruinDelims("""this is a test
            |here's another test
            |catch me if you can!
            |""".stripMargin)

        val results = unspool(input).through(multipartPipe(boundary))
        val multipartMaterialized = results.compile.last.map(_.get).unsafeRunSync()
        val headers = multipartMaterialized.parts.foldLeft(Headers.empty)(_ ++ _.headers)
        val bodies = multipartMaterialized.parts
          .foldLeft(Stream.empty.covary[IO]: Stream[IO, Byte])(_ ++ _.body)
          .through(asciiDecode)
          .compile
          .foldMonoid

        headers mustEqual expectedHeaders
        bodies.attempt.unsafeRunSync() must beRight(expected)
      }

      "produce the body from a two-part input" in {
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

        val results = unspool(input).through(multipartPipe(boundary))
        val multipartMaterialized = results.compile.last.map(_.get).unsafeRunSync()
        val bodies = multipartMaterialized
          .parts(1)
          .body
          .through(asciiDecode)
          .compile
          .foldMonoid

        bodies.attempt.unsafeRunSync() must beRight("bar")
      }

      "parse uneven input properly" in {
        val unprocessed =
          Stream
            .emits(List(
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
                  |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI--""".stripMargin
            ).map(_.replace("\n", "\r\n"))
              .map(str => Chunk.array(str.getBytes(StandardCharsets.UTF_8))))
            .flatMap(Stream.chunk)
            .covary[IO]

        val results = unprocessed.through(multipartPipe(boundary))
        val multipartMaterialized = results.compile.last.map(_.get).unsafeRunSync()
        val bodies = multipartMaterialized
          .parts(1)
          .body
          .through(asciiDecode)
          .compile
          .foldMonoid

        bodies.attempt.unsafeRunSync() must beRight("bar")
      }

      Fragments.foreach(List.range(0, 100)) { count =>
        s"parse randomized chunk length properly iteration #$count" in {
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

          val results = unprocessed.through(multipartPipe(boundary))
          val multipartMaterialized = results.compile.last.map(_.get).unsafeRunSync()
          val bodies = multipartMaterialized
            .parts(1)
            .body
            .through(asciiDecode)
            .compile
            .foldMonoid

          bodies.attempt.unsafeRunSync() must beRight("bar")
        }
      }

      "produce the correct headers from a two part input" in {
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
        val results = unspool(input).through(multipartPipe(boundaryTest))

        val multipartMaterialized = results.compile.last.map(_.get).unsafeRunSync()
        val headers =
          multipartMaterialized.parts.foldLeft(List.empty[Headers])((l, r) => l ::: List(r.headers))
        headers mustEqual List(
          Headers.of(
            `Content-Disposition`("form-data", Map("name" -> "field1")),
            `Content-Type`(MediaType.text.plain)
          ),
          Headers.of(
            `Content-Disposition`("form-data", Map("name" -> "field2"))
          )
        )
      }

      "parse parts lazily" in {
        //Intentionally mangle the end, which would fail if we consume the whole thing,
        //but not if we only take one part, as each part should parse lazily
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
        val results = unspool(input).through(partsPipe(boundaryTest))

        val firstPart = results.take(1).compile.last.map(_.get).unsafeRunSync()
        val confirmedError = results.compile.drain.attempt.unsafeRunSync()

        firstPart.headers must_== Headers.of(
          `Content-Disposition`("form-data", Map("name" -> "field1")),
          `Content-Type`(MediaType.text.plain))
        firstPart.body
          .through(text.utf8Decode[IO])
          .compile
          .foldMonoid
          .unsafeRunSync() must_== "Text_Field_1"
        confirmedError must beAnInstanceOf[Left[MalformedMessageBodyFailure, _]]
      }

      Fragments.foreach(List(1, 2, 3, 5, 8, 13, 21, 987)) { chunkSize =>
        s"drain the epilogue with chunk size $chunkSize" in {
          val unprocessedInput =
            """
            |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
            |Content-Disposition: form-data; name="foo"
            |
            |bar
            |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI--
            |This should be ignored, but still drained!""".stripMargin

          val input = ruinDelims(unprocessedInput)

          val checkReachedTheEnd: IO[Boolean] = for {
            // This should be false until we drain the whole input.
            ref <- Ref[IO].of(false)
            trackedInput = unspool(input, chunkSize) ++ Stream.eval(ref.set(true)).drain

            _ <- trackedInput.through(multipartPipe(boundary)).compile.drain

            reachedTheEnd <- ref.get
          } yield reachedTheEnd

          checkReachedTheEnd.unsafeRunSync() must_=== true
        }
      }

      "fail with an MalformedMessageBodyFailure without an end line" in {
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

        val results = unspool(input).through(multipartPipe(boundary))

        results.compile.toVector.unsafeRunSync() must throwA[MalformedMessageBodyFailure]
      }
    }
  }

  multipartParserTests(
    "Default parser",
    MultipartParser.parseStreamed[IO](_),
    MultipartParser.parseStreamed[IO],
    MultipartParser.parseToPartsStream[IO](_))

  multipartParserTests(
    "mixed file parser",
    MultipartParser.parseStreamedFile[IO](_),
    MultipartParser.parseStreamedFile[IO](_, _),
    MultipartParser.parseToPartsStreamedFile[IO](_)
  )

  "Multipart mixed file parser" should {
    "truncate parts when limit set" in {
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

      val multipartMaterialized = results.compile.last.map(_.get).unsafeRunSync()
      val headers =
        multipartMaterialized.parts.foldLeft(List.empty[Headers])((l, r) => l ::: List(r.headers))
      headers mustEqual List(
        Headers.of(
          `Content-Disposition`("form-data", Map("name" -> "field1")),
          `Content-Type`(MediaType.text.plain)
        )
      )
    }

    "Fail parsing when parts limit exceeded if set fail as option" in {
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
        MultipartParser.parseStreamedFile[IO](boundaryTest, maxParts = 1, failOnLimit = true))

      results.compile.last.map(_.get).unsafeRunSync() must throwA[MalformedMessageBodyFailure]
    }
  }
}
