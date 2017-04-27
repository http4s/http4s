package org.http4s
package multipart

import org.http4s.headers._
import org.specs2.mutable._

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

  "form parsing" should {
    "produce the body from a single part input" in {

      val Limit = 15

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

      def unspool(str: String): Process0[ByteVector] = {
        if (str.isEmpty) {
          halt
        } else if (str.length <= Limit) {
          emit(ByteVector view (str getBytes "ASCII"))
        } else {
          val front = str.substring(0, Limit)
          val back = str.substring(Limit)

          emit(ByteVector view (front getBytes "ASCII")) ++ unspool(back)
        }
      }

      val results: Process0[Headers \/ ByteVector] = unspool(input) pipe MultipartParser.parse(boundary)

      val (headers, bv) = results.toVector.foldLeft((Headers.empty, ByteVector.empty)) {
        case ((hsAcc, bvAcc), \/-(bv)) => (hsAcc, bvAcc ++ bv)
        case ((hsAcc, bvAcc), -\/(hs)) => (hsAcc ++ hs, bvAcc)
      }

      headers mustEqual (expectedHeaders)
      bv.decodeAscii mustEqual Right(expected)
    }

    "produce the body from a single part input without limit" in {
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

      def unspool(str: String): Process0[ByteVector] = emit(ByteVector view (str getBytes "ASCII"))

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
      val unprocessedInput = """--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
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

      val expectedHeaders = Headers(
        `Content-Disposition`("form-data", Map("name" -> "upload", "filename" -> "integration.txt")),
        `Content-Type`(MediaType.`application/octet-stream`),
        Header("Content-Transfer-Encoding", "binary")
      )

      val expected = ruinDelims("""this is a test
              |here's another test
              |catch me if you can!
              |""".stripMargin)

      def unspool(str: String): Process0[ByteVector] = emit(ByteVector view (str getBytes "ASCII"))

      val results: Process0[Headers \/ ByteVector] = unspool(input) pipe MultipartParser.parse(boundary)

      val (headers, bv) = results.toVector.foldLeft((Headers.empty, ByteVector.empty)) {
        case ((hsAcc, bvAcc), \/-(bv)) => (hsAcc, bvAcc ++ bv)
        case ((hsAcc, bvAcc), -\/(hs)) => (hsAcc ++ hs, ByteVector.empty)
      }

      bv.decodeAscii mustEqual Right("bar")
    }

    "fail with an MalformedMessageBodyFailure without an end line" in {
      val unprocessedInput = """--_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI
        |Content-Disposition: form-data; name="upload"; filename="integration.txt"
        |Content-Type: application/octet-stream
        |Content-Transfer-Encoding: binary
        |
        |this is a test
        |here's another test
        |catch me if you can!""".stripMargin
      val input = ruinDelims(unprocessedInput)

      def unspool(str: String): Process0[ByteVector] = emit(ByteVector view (str getBytes "ASCII"))
      val results: Process0[Headers \/ ByteVector] = unspool(input) pipe MultipartParser.parse(boundary)

      results.toVector must throwAn[MalformedMessageBodyFailure]
    }
  }
}
