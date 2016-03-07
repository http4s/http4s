package org.http4s

import org.specs2.mutable._

import scalaz.{-\/, \/-, \/}
import scalaz.stream.{Process, Process0}

import scodec.bits.ByteVector

object FormParserSpecs extends Specification {
  import Process._

  "form parsing" should {
    "produce the body from a single part input" in {
      def ruinDelims(str: String) = augmentString(str) flatMap {
        case '\n' => "\r\n"
        case c => c.toString
      }

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

      val results: Process0[Map[String, String] \/ ByteVector] = unspool(input) pipe FormParser.parse

      val bytes = results.toVector collect {
        case \/-(bv) => bv
      }

      val bv = bytes reduce { _ ++ _ }

      bv.decodeAscii mustEqual Right(expected)
    }

    "produce the body from a single part input without limit" in {
      def ruinDelims(str: String) = augmentString(str) flatMap {
        case '\n' => "\r\n"
        case c => c.toString
      }

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

      val expected = ruinDelims("""this is a test
              |here's another test
              |catch me if you can!
              |""".stripMargin)

      def unspool(str: String): Process0[ByteVector] = emit(ByteVector view (str getBytes "ASCII"))

      val results: Process0[Map[String, String] \/ ByteVector] = unspool(input) pipe FormParser.parse

      val bytes = results.toVector collect {
        case \/-(bv) => bv
      }

      val bv = bytes reduce { _ ++ _ }

      bv.decodeAscii mustEqual Right(expected)
    }
  }
}
