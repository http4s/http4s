package org.http4s
package multipart

import org.http4s.headers._
import org.specs2.mutable._
import org.specs2.specification.core.Fragments

import scalaz.{-\/, \/-, \/}
import scalaz.stream.{Process, Process0}

import scodec.bits.ByteVector

object MultipartParserSpec extends Specification {
  import Process._

  val boundary = Boundary("_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI")

  def ruinDelims(str: String) = augmentString(str) flatMap {
    case '\n' => "\r\n"
    case c => c.toString
  }

  def unspool(str: String, limit: Int = Int.MaxValue): Process0[ByteVector] = {
    if (str.isEmpty) {
      halt
    } else if (str.length <= limit) {
      emit(ByteVector view (str getBytes "ASCII"))
    } else {
      val (front, back) = str.splitAt(limit)
      emit(ByteVector view (front getBytes "ASCII")) ++ unspool(back, limit)
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
          Header(fn"Content-Transfer-Encoding", fv"binary")
        )

        val expected = ruinDelims("""this is a test
              |here's another test
              |catch me if you can!
              |""".stripMargin)

        val results: Process0[Headers \/ ByteVector] =
          unspool(input, chunkSize) pipe MultipartParser.parse(boundary)

        val (headers, bv) = results.toVector.foldLeft((Headers.empty, ByteVector.empty)) {
          case ((hsAcc, bvAcc), \/-(bv)) => (hsAcc, bvAcc ++ bv)
          case ((hsAcc, bvAcc), -\/(hs)) => (hsAcc ++ hs, bvAcc)
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
      val results: Process0[Headers \/ ByteVector] =
        unspool(input, 15) pipe MultipartParser.parse(boundary)

      val expectedHeaders = Headers(
        `Content-Disposition`("form-data", Map("name" -> "upload", "filename" -> "integration.txt")),
        `Content-Type`(MediaType.`application/octet-stream`),
        Header(fn"Content-Transfer-Encoding", fv"binary")
      )

      val expected = ruinDelims("""this is a test
              |here's another test
              |catch me if you can!
              |""".stripMargin)

      val (headers, bv) = results.toVector.foldLeft((Headers.empty, ByteVector.empty)) {
        case ((hsAcc, bvAcc), \/-(bv)) => (hsAcc, bvAcc ++ bv)
        case ((hsAcc, bvAcc), -\/(hs)) => (hsAcc ++ hs, bvAcc)
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
        Header(fn"Content-Transfer-Encoding", fv"binary")
      )

      val expected = ruinDelims("""this is a test
              |here's another test
              |catch me if you can!
              |""".stripMargin)

      val preamble = emit(ByteVector.view("Misery is the river of the world".getBytes("ASCII"))).repeat.take(10)
      val epilogue = emit(ByteVector.view("Everybody Row!\n".getBytes("ASCII"))).repeat.take(10)

      val results: Process0[Headers \/ ByteVector] =
        (preamble ++ emit(ByteVector.view("\r\n".getBytes("ASCII"))) ++ unspool(input, 15) ++ epilogue).pipe(MultipartParser.parse(boundary))

      val (headers, bv) = results.toVector.foldLeft((Headers.empty, ByteVector.empty)) {
        case ((hsAcc, bvAcc), \/-(bv)) => (hsAcc, bvAcc ++ bv)
        case ((hsAcc, bvAcc), -\/(hs)) => (hsAcc ++ hs, bvAcc)
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

      val results: Process0[Headers \/ ByteVector] =
        unspool(input, 15) pipe MultipartParser.parse(boundary, 100)

      results.toVector must throwA(MalformedMessageBodyFailure("Part header was longer than 100-byte limit"))
    }

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
        Header(fn"Content-Transfer-Encoding", fv"binary")
      )

      val body = emit(ByteVector.view("Misery is the river of the world".getBytes("ASCII"))).repeat.take(100000)

      val results: Process0[Headers \/ ByteVector] =
        (unspool(input) ++ body ++ emit(ByteVector.view("\r\n".getBytes("ASCII"))) ++ unspool(end)).pipe(MultipartParser.parse(boundary))

      val headers = results.toVector.foldLeft(Headers.empty) {
        case (hsAcc, \/-(_)) => hsAcc
        case (hsAcc, -\/(hs)) => hsAcc ++ hs
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
        Header(fn"Content-Transfer-Encoding", fv"binary")
      )

      val expected = ruinDelims("""this is a test
              |here's another test
              |catch me if you can!
              |""".stripMargin)

      val results: Process0[Headers \/ ByteVector] = unspool(input) pipe MultipartParser.parse(boundary)

      val bytes = results.toVector collect {
        case \/-(bv) => bv
      }

      val (headers, bv) = results.toVector.foldLeft((Headers.empty, ByteVector.empty)) {
        case ((hsAcc, bvAcc), \/-(bv)) => (hsAcc, bvAcc ++ bv)
        case ((hsAcc, bvAcc), -\/(hs)) => (hsAcc ++ hs, bvAcc)
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

      val results: Process0[Headers \/ ByteVector] = unspool(input) pipe MultipartParser.parse(boundary)

      val (headers, bv) = results.toVector.foldLeft((Headers.empty, ByteVector.empty)) {
        case ((hsAcc, bvAcc), \/-(bv)) => (hsAcc, bvAcc ++ bv)
        case ((hsAcc, bvAcc), -\/(hs)) => (hsAcc ++ hs, ByteVector.empty)
      }

      bv.decodeAscii mustEqual Right("bar")
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

      val results: Process0[Headers \/ ByteVector] = unspool(input) pipe MultipartParser.parse(boundary)

      results.toVector must throwAn[MalformedMessageBodyFailure]
    }
  }
}
