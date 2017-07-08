package org.http4s
package multipart

import org.http4s.headers._
import org.specs2.mutable._
import org.specs2.specification.core.Fragments

import fs2._
import scodec.bits.ByteVector

object MultipartParserSpec extends Specification {

  val boundary = Boundary("_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI")

  def ruinDelims(str: String) = augmentString(str) flatMap {
    case '\n' => "\r\n"
    case c => c.toString
  }

  def unspool(str: String, limit: Int = Int.MaxValue): Stream[Task, Byte] = {
    if (str.isEmpty) {
      Stream.empty
    } else if (str.length <= limit) {
      Stream.emits(ByteVector.view(str getBytes "ASCII").toSeq)
    } else {
      val (front, back) = str.splitAt(limit)
      Stream.emits(ByteVector.view(front getBytes "ASCII").toSeq) ++ unspool(back, limit)
    }
  }

  "form parsing" should {
    Fragments.foreach(List(1, 2, 3, 5, 8, 13, 21, /* nah let's skip ahead */ 987)) { chunkSize =>
      s"produce the body from a single part with chunk size ${chunkSize}" in {
        val unprocessedInput = """
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
          `Content-Disposition`("form-data", Map("name" -> "upload", "filename" -> "integration.txt")),
          `Content-Type`(MediaType.`application/octet-stream`),
          Header("Content-Transfer-Encoding", "binary")
        )

        val expected = ruinDelims("""this is a test
              |here's another test
              |catch me if you can!
              |""".stripMargin)

        val results: Stream[Task, Either[Headers,Byte]] =
          unspool(input, chunkSize).through(MultipartParser.parse(boundary))

        val (headers, bv) = results.runLog.unsafeRun().foldLeft((Headers.empty, ByteVector.empty)) {
          case ((hsAcc, bvAcc), Right(byte)) => (hsAcc, bvAcc ++ ByteVector.fromByte(byte))
          case ((hsAcc, bvAcc), Left(hs)) => (hsAcc ++ hs, bvAcc)
        }

        headers mustEqual (expectedHeaders)
        bv.decodeAscii mustEqual Right(expected)
      }
    }

    "produce the body from a single part that doesn't start with a CRLF" in {
      val unprocessedInput = """--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
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
      val results: Stream[Task, Either[Headers,Byte]] =
        unspool(input, 15).through(MultipartParser.parse(boundary))

      val expectedHeaders = Headers(
        `Content-Disposition`("form-data", Map("name" -> "upload", "filename" -> "integration.txt")),
        `Content-Type`(MediaType.`application/octet-stream`),
        Header("Content-Transfer-Encoding", "binary")
      )

      val expected = ruinDelims("""this is a test
              |here's another test
              |catch me if you can!
              |""".stripMargin)

      val (headers, bv) = results.runLog.unsafeRun().foldLeft((Headers.empty, ByteVector.empty)) {
        case ((hsAcc, bvAcc), Right(byte)) => (hsAcc, bvAcc ++ ByteVector.fromByte(byte))
        case ((hsAcc, bvAcc), Left(hs)) => (hsAcc ++ hs, bvAcc)
      }

      headers mustEqual (expectedHeaders)
      bv.decodeAscii mustEqual Right(expected)
    }

    "discard preamble and epilogue" in {
      val unprocessedInput = """--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
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
        `Content-Disposition`("form-data", Map("name" -> "upload", "filename" -> "integration.txt")),
        `Content-Type`(MediaType.`application/octet-stream`),
        Header("Content-Transfer-Encoding", "binary")
      )

      val expected = ruinDelims("""this is a test
              |here's another test
              |catch me if you can!
              |""".stripMargin)


      val preamble : Stream[Task, Byte] =
        Stream.constant("Misery is the river of the world")
          .take(10)
          .covary[Task]
          .through(text.utf8Encode)

      val crlf : Stream[Task, Byte] =
        Stream.emit(Boundary.CRLF)
          .covary[Task]
          .through(text.utf8Encode)

      val epilogue : Stream[Task, Byte] =
        Stream.constant("Everybody Row!\n")
          .take(10)
          .covary[Task]
          .through(text.utf8Encode)

      val results: Stream[Task, Either[Headers,Byte]] = (
          preamble ++
          crlf ++
          unspool(input, 15) ++
          epilogue
        ).through(MultipartParser.parse(boundary))

      val (headers, bv) = results.runLog.unsafeRun().foldLeft((Headers.empty, ByteVector.empty)) {
        case ((hsAcc, bvAcc), Right(byte)) => (hsAcc, bvAcc ++ ByteVector.fromByte(byte))
        case ((hsAcc, bvAcc), Left(hs)) => (hsAcc ++ hs, bvAcc)
      }

      headers mustEqual (expectedHeaders)
      bv.decodeAscii mustEqual Right(expected)
    }

    "fail if the header is too large" in {
      // This is a valid multipart body, but in this example, we're imposing an
      // absurdly low cap in the argument to MultipartParser.parse to trigger failure.
      val unprocessedInput = """
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

      val results: Stream[Task, Either[Headers,Byte]] =
        unspool(input, 15).through(MultipartParser.parse(boundary, 100))

      results.runLog.unsafeRun() must throwA(MalformedMessageBodyFailure("Part header was longer than 100-byte limit"))
    }.pendingUntilFixed("Due to Buffering All, Irrelevant")

    "handle an miserably large body on one line" in {
      val input = ruinDelims("""--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
        |Content-Disposition: form-data; name="upload"; filename="integration.txt"
        |Content-Type: application/octet-stream
        |Content-Transfer-Encoding: binary
        |
        """.stripMargin)
      val end = "--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI--"

      val expectedHeaders = Headers(
        `Content-Disposition`("form-data", Map("name" -> "upload", "filename" -> "integration.txt")),
        `Content-Type`(MediaType.`application/octet-stream`),
        Header("Content-Transfer-Encoding", "binary")
      )

      val crlf : Stream[Task, Byte] =
        Stream.emit(Boundary.CRLF)
          .covary[Task]
          .through(text.utf8Encode)

      val body : Stream[Task, Byte] = Stream.constant("Misery is the river of the world")
        .take(100000)
        .covary[Task]
        .through(text.utf8Encode)

      val results: Stream[Task, Either[Headers,Byte]] = (
          unspool(input) ++
            body ++
            crlf ++
            unspool(end)
        ).through(MultipartParser.parse(boundary))

      val headers = results.runLog.unsafeRun().foldLeft(Headers.empty) {
        case (hsAcc, Right(_)) => hsAcc
        case (hsAcc, Left(hs)) => hsAcc ++ hs
      }

      headers mustEqual (expectedHeaders)
    }

    "produce the body from a single part input of one chunk" in {
      val unprocessedInput = """
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
        `Content-Disposition`("form-data", Map("name" -> "upload", "filename" -> "integration.txt")),
        `Content-Type`(MediaType.`application/octet-stream`),
        Header("Content-Transfer-Encoding", "binary")
      )

      val expected = ruinDelims("""this is a test
              |here's another test
              |catch me if you can!
              |""".stripMargin)

      val results: Stream[Task, Either[Headers,Byte]] = unspool(input).through(MultipartParser.parse(boundary))

      val bytes = results.runLog.unsafeRun().collect {
        case Right(bv) => bv
      }

      val (headers, bv) = results.runLog.unsafeRun().foldLeft((Headers.empty, ByteVector.empty)) {
        case ((hsAcc, bvAcc), Right(byte)) => (hsAcc, bvAcc ++ ByteVector.fromByte(byte))
        case ((hsAcc, bvAcc), Left(hs)) => (hsAcc ++ hs, bvAcc)
      }

      headers mustEqual (expectedHeaders)
      bv.decodeAscii mustEqual Right(expected)
    }

    "produce the body from a two-part input" in {
      val unprocessedInput = """
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

      val results: Stream[Task, Either[Headers,Byte]] = unspool(input).through(MultipartParser.parse(boundary))

      val (headers, bv) = results.runLog.unsafeRun().foldLeft((Headers.empty, ByteVector.empty)) {
        case ((hsAcc, bvAcc), Right(byte)) => (hsAcc, bvAcc ++ ByteVector.fromByte(byte))
        case ((hsAcc, bvAcc), Left(hs)) => (hsAcc ++ hs, bvAcc)
      }



      bv.decodeAscii mustEqual Right("bar")
    }

    "produce the correct headers from a two part input" in {
      val unprocessedInput=
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
      val results: Stream[Task, Either[Headers,Byte]] = unspool(input).through(MultipartParser.parse(boundaryTest))

      val (headers, bv) = results.runLog.unsafeRun().foldLeft((List.empty[Headers], ByteVector.empty)) {
        case ((hsAcc, bvAcc), Right(byte)) => (hsAcc, bvAcc ++ ByteVector.fromByte(byte))
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
      val unprocessedInput = """
        |--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
        |Content-Disposition: form-data; name="upload"; filename="integration.txt"
        |Content-Type: application/octet-stream
        |Content-Transfer-Encoding: binary
        |
        |this is a test
        |here's another test
        |catch me if you can!""".stripMargin
      val input = ruinDelims(unprocessedInput)

      val results: Stream[Task, Either[Headers,Byte]] = unspool(input).through(MultipartParser.parse(boundary))

      results.runLog.unsafeRun() must throwAn[MalformedMessageBodyFailure]
    }
  }

}
