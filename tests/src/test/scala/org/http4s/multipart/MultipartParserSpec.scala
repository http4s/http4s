package org.http4s
package multipart

import java.nio.charset.StandardCharsets

import cats.effect._
import fs2._
import org.http4s.headers._
import org.http4s.util._
import org.specs2.mutable._
import org.specs2.specification.core.Fragments
import scodec.bits.ByteVector

object MultipartParserSpec extends Specification {

  val boundary = Boundary("_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI")

  def ruinDelims(str: String) = augmentString(str).flatMap {
    case '\n' => "\r\n"
    case c => c.toString
  }

  def jumble(str: String): Stream[IO, Byte] = {
    val rand = new scala.util.Random()

    def jumbleAccum(s: String, acc: Stream[IO, Byte]): Stream[IO, Byte] =
      if (s.length <= 1) {
        acc ++ Stream.chunk(Chunk.bytes(s.getBytes()))
      } else {
        val (l, r) = s.splitAt(rand.nextInt(s.length - 1) + 1)
        jumbleAccum(r, acc ++ Stream.chunk(Chunk.bytes(l.getBytes)))
      }

    jumbleAccum(str, Stream.empty)
  }

  def unspool(
      str: String,
      limit: Int = Int.MaxValue,
      charset: java.nio.charset.Charset = StandardCharsets.US_ASCII): Stream[IO, Byte] =
    if (str.isEmpty) {
      Stream.empty
    } else if (str.length <= limit) {
      Stream.emits(ByteVector.view(str.getBytes(charset)).toSeq)
    } else {
      val (front, back) = str.splitAt(limit)
      Stream.emits(ByteVector.view(front.getBytes(charset)).toSeq) ++ unspool(back, limit, charset)
    }

  "form parsing" should {
    Fragments.foreach(List(1, 2, 3, 5, 8, 13, 21, /* nah let's skip ahead */ 987)) { chunkSize =>
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

        val expectedHeaders = Headers(
          `Content-Disposition`(
            "form-data",
            Map("name" -> "upload", "filename" -> "integration.txt")),
          `Content-Type`(MediaType.`application/octet-stream`),
          Header("Content-Transfer-Encoding", "binary")
        )

        val expected = ruinDelims("""this is a test
              |here's another test
              |catch me if you can!
              |""".stripMargin)

        val results =
          unspool(input, chunkSize).through(MultipartParser.parse(boundary))

        val (headers, bv) =
          results.compile.toVector.unsafeRunSync().foldLeft((Headers.empty, ByteVector.empty)) {
            case ((hsAcc, bvAcc), Right(bv)) => (hsAcc, bvAcc ++ bv)
            case ((hsAcc, bvAcc), Left(hs)) => (hsAcc ++ hs, bvAcc)
          }

        headers mustEqual expectedHeaders
        bv.decodeAscii must beRight(expected)
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
        unspool(input, 15).through(MultipartParser.parse(boundary))

      val expectedHeaders = Headers(
        `Content-Disposition`(
          "form-data",
          Map("name" -> "upload", "filename" -> "integration.txt")),
        `Content-Type`(MediaType.`application/octet-stream`),
        Header("Content-Transfer-Encoding", "binary")
      )

      val expected = ruinDelims("""this is a test
              |here's another test
              |catch me if you can!
              |""".stripMargin)

      val (headers, bv) =
        results.compile.toVector.unsafeRunSync().foldLeft((Headers.empty, ByteVector.empty)) {
          case ((hsAcc, bvAcc), Right(bv)) => (hsAcc, bvAcc ++ bv)
          case ((hsAcc, bvAcc), Left(hs)) => (hsAcc ++ hs, bvAcc)
        }

      headers mustEqual expectedHeaders
      bv.decodeAscii must beRight(expected)
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

      val expectedHeaders = Headers(
        `Content-Disposition`(
          "form-data",
          Map("name" -> "upload", "filename" -> "integration.txt")),
        `Content-Type`(MediaType.`application/octet-stream`),
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
      ).through(MultipartParser.parse(boundary))

      val (headers, bv) =
        results.compile.toVector.unsafeRunSync().foldLeft((Headers.empty, ByteVector.empty)) {
          case ((hsAcc, bvAcc), Right(bv)) => (hsAcc, bvAcc ++ bv)
          case ((hsAcc, bvAcc), Left(hs)) => (hsAcc ++ hs, bvAcc)
        }

      headers mustEqual expectedHeaders
      bv.decodeAscii must beRight(expected)
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

      val results =
        unspool(input, 15).through(MultipartParser.parse(boundary))

      results.compile.toVector.unsafeRunSync() must throwA(
        MalformedMessageBodyFailure("Part header was longer than 100-byte limit"))
    }.pendingUntilFixed("Due to Buffering All, Irrelevant")

    "handle a miserably large body on one line" in {
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
          Map("name" -> "upload", "filename" -> "integration.txt")),
        `Content-Type`(MediaType.`application/octet-stream`),
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
      ).through(MultipartParser.parse(boundary))

      val headers = results.compile.toVector.unsafeRunSync().foldLeft(Headers.empty) {
        case (hsAcc, Right(_)) => hsAcc
        case (hsAcc, Left(hs)) => hsAcc ++ hs
      }

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

      val expectedHeaders = Headers(
        `Content-Disposition`(
          "form-data",
          Map("name" -> "upload", "filename" -> "integration.txt")),
        `Content-Type`(MediaType.`application/octet-stream`),
        Header("Content-Transfer-Encoding", "binary")
      )

      val expected = ruinDelims("""this is a test
              |here's another test
              |catch me if you can!
              |""".stripMargin)

      val results = unspool(input).through(MultipartParser.parse(boundary))

      val (headers, bv) =
        results.compile.toVector.unsafeRunSync().foldLeft((Headers.empty, ByteVector.empty)) {
          case ((hsAcc, bvAcc), Right(bv)) => (hsAcc, bvAcc ++ bv)
          case ((hsAcc, bvAcc), Left(hs)) => (hsAcc ++ hs, bvAcc)
        }

      headers mustEqual expectedHeaders
      bv.decodeAscii must beRight(expected)
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

      val results = unspool(input).through(MultipartParser.parse(boundary))

      // Accumulator Bytevector Resets on New Header, so bv represents ByteVector of last Part.
      val (_, bv) =
        results.compile.toVector.unsafeRunSync().foldLeft((Headers.empty, ByteVector.empty)) {
          case ((hsAcc, bvAcc), Right(bv)) => (hsAcc, bvAcc ++ bv)
          case ((hsAcc, _), Left(hs)) => (hsAcc ++ hs, ByteVector.empty)
        }

      bv.decodeUtf8 must beRight("bar")
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
      val results = unspool(input).through(MultipartParser.parse(boundaryTest))

      val (headers, _) =
        results.compile.toVector.unsafeRunSync().foldLeft((List.empty[Headers], ByteVector.empty)) {
          case ((hsAcc, bvAcc), Right(bv)) => (hsAcc, bvAcc ++ bv)
          case ((hsAcc, bvAcc), Left(hs)) => (hs :: hsAcc, bvAcc)
        }

      headers.reverse mustEqual List(
        Headers(
          `Content-Disposition`("form-data", Map("name" -> "field1")),
          `Content-Type`(MediaType.`text/plain`)
        ),
        Headers(
          `Content-Disposition`("form-data", Map("name" -> "field2"))
        )
      )
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

      val results = unspool(input).through(MultipartParser.parse(boundary))

      results.compile.toVector.unsafeRunSync() must throwA[MalformedMessageBodyFailure]
    }
  }

  "form streaming parsing" should {
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

        val expectedHeaders = Headers(
          `Content-Disposition`(
            "form-data",
            Map("name" -> "upload", "filename" -> "integration.txt")),
          `Content-Type`(MediaType.`application/octet-stream`),
          Header("Content-Transfer-Encoding", "binary")
        )

        val expected = ruinDelims("""this is a test
                                    |here's another test
                                    |catch me if you can!
                                    |""".stripMargin)

        val results =
          unspool(input, chunkSize).through(MultipartParser.parseStreamed(boundary))

        val multipartMaterialized = results.compile.last.map(_.get).unsafeRunSync()
        val headers = multipartMaterialized.parts.foldLeft(Headers.empty)(_ ++ _.headers)
        val bodies = multipartMaterialized.parts
          .foldLeft(Stream.empty.covary[IO]: Stream[IO, Byte])(_ ++ _.body)
          .through(asciiDecode)
          .compile
          .fold("")(_ ++ _)

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
        unspool(input, 15).through(MultipartParser.parseStreamed(boundary))
      val multipartMaterialized = results.compile.last.map(_.get).unsafeRunSync()

      val expectedHeaders = Headers(
        `Content-Disposition`(
          "form-data",
          Map("name" -> "upload", "filename" -> "integration.txt")),
        `Content-Type`(MediaType.`application/octet-stream`),
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
          .fold("")(_ ++ _)

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
        unspool(input, 15, StandardCharsets.UTF_8).through(MultipartParser.parseStreamed(boundary))
      val multipartMaterialized = results.compile.last.map(_.get).unsafeRunSync()

      val expectedHeaders = Headers(
        `Content-Disposition`("form-data", Map("name" -> "http4s很棒", "filename" -> "我老婆太漂亮.txt")),
        `Content-Type`(MediaType.`application/octet-stream`),
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
          .fold("")(_ ++ _)

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

      val expectedHeaders = Headers(
        `Content-Disposition`(
          "form-data",
          Map("name" -> "upload", "filename" -> "integration.txt")),
        `Content-Type`(MediaType.`application/octet-stream`),
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
      ).through(MultipartParser.parseStreamed(boundary))

      val multipartMaterialized = results.compile.last.map(_.get).unsafeRunSync()
      val headers = multipartMaterialized.parts.foldLeft(Headers.empty)(_ ++ _.headers)
      val bodies = multipartMaterialized.parts
        .foldLeft(Stream.empty.covary[IO]: Stream[IO, Byte])(_ ++ _.body)
        .through(asciiDecode)
        .compile
        .fold("")(_ ++ _)

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
        unspool(input, 15).through(MultipartParser.parseStreamed(boundary, maxSize))

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

      val expectedHeaders = Headers(
        `Content-Disposition`(
          "form-data",
          Map("name" -> "upload", "filename" -> "integration.txt")),
        `Content-Type`(MediaType.`application/octet-stream`),
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
      ).through(MultipartParser.parseStreamed(boundary))

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

      val expectedHeaders = Headers(
        `Content-Disposition`(
          "form-data",
          Map("name" -> "upload", "filename" -> "integration.txt")),
        `Content-Type`(MediaType.`application/octet-stream`),
        Header("Content-Transfer-Encoding", "binary")
      )

      val expected = ruinDelims("""this is a test
                                  |here's another test
                                  |catch me if you can!
                                  |""".stripMargin)

      val results = unspool(input).through(MultipartParser.parseStreamed(boundary))
      val multipartMaterialized = results.compile.last.map(_.get).unsafeRunSync()
      val headers = multipartMaterialized.parts.foldLeft(Headers.empty)(_ ++ _.headers)
      val bodies = multipartMaterialized.parts
        .foldLeft(Stream.empty.covary[IO]: Stream[IO, Byte])(_ ++ _.body)
        .through(asciiDecode)
        .compile
        .fold("")(_ ++ _)

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

      val results = unspool(input).through(MultipartParser.parseStreamed(boundary))
      val multipartMaterialized = results.compile.last.map(_.get).unsafeRunSync()
      val bodies = multipartMaterialized
        .parts(1)
        .body
        .through(asciiDecode)
        .compile
        .fold("")(_ ++ _)

      bodies.attempt.unsafeRunSync() must beRight("bar")
    }

    "parse uneven input properly" in {
      val unprocessed =
        Stream
          .segment(
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
                |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI--""".stripMargin
            ).map(_.replaceAllLiterally("\n", "\r\n"))
              .map(str => Segment.chunk(Chunk.bytes(str.getBytes)))
              .foldLeft(Segment.empty[Byte])(_ ++ _)
          )
          .covary[IO]

      val results = unprocessed.through(MultipartParser.parseStreamed(boundary))
      val multipartMaterialized = results.compile.last.map(_.get).unsafeRunSync()
      val bodies = multipartMaterialized
        .parts(1)
        .body
        .through(asciiDecode)
        .compile
        .fold("")(_ ++ _)

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
            |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI--""".stripMargin.replaceAllLiterally("\n", "\r\n")

        val unprocessed = jumble(unprocessedInput)

        val results = unprocessed.through(MultipartParser.parseStreamed(boundary))
        val multipartMaterialized = results.compile.last.map(_.get).unsafeRunSync()
        val bodies = multipartMaterialized
          .parts(1)
          .body
          .through(asciiDecode)
          .compile
          .fold("")(_ ++ _)

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
      val results = unspool(input).through(MultipartParser.parseStreamed(boundaryTest))

      val multipartMaterialized = results.compile.last.map(_.get).unsafeRunSync()
      val headers =
        multipartMaterialized.parts.foldLeft(List.empty[Headers])((l, r) => l ::: List(r.headers))
      headers mustEqual List(
        Headers(
          `Content-Disposition`("form-data", Map("name" -> "field1")),
          `Content-Type`(MediaType.`text/plain`)
        ),
        Headers(
          `Content-Disposition`("form-data", Map("name" -> "field2"))
        )
      )
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

      val results = unspool(input).through(MultipartParser.parseStreamed(boundary))

      results.compile.toVector.unsafeRunSync() must throwA[MalformedMessageBodyFailure]
    }
  }
}
