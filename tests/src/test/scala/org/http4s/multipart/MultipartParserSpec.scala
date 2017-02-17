package org.http4s
package multipart

import org.http4s.headers._
import org.specs2.mutable._

import fs2._
import fs2.Stream._
import cats.syntax.either._

import scodec.bits.ByteVector

object MultipartParserSpec extends Specification {

  val boundary = Boundary("_5PHqf8_Pl1FCzBuT5o_mVZg36k67UYI")

  def byteStreamtoStreamByteVector: Pipe[Task, Byte, ByteVector] = _.mapChunks(chunk => Chunk.singleton(ByteVector(chunk.toArray)))
  def byteVectorStreamtoStreamByte: Pipe[Task, ByteVector, Byte] = _.flatMap(bv => Stream.emits(bv.toSeq))
  def eitherByteVectorStreamtoStreamByte : Pipe[Task, Either[Headers, ByteVector], Either[Headers, Byte]] = _.flatMap{
    case Right(bv) => Stream.emits(bv.toSeq.map(Either.right))
    case Left(headers) => Stream.emit(Either.left(headers))
  }

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

      def unspool(str: String): Stream[Task, Byte] = {
        if (str.length <= Limit) {
          emits(str getBytes "ASCII")
        } else {
          val front = str.substring(0, Limit)
          val back = str.substring(Limit)

          emits(front getBytes "ASCII") ++ unspool(back)
        }
      }

      val results: Stream[Task, Either[Headers, Byte]] =
        unspool(input)
        .through(MultipartParser.parse(boundary))



      val (headers, byteStream) = results.runLog.map{_.foldLeft((Headers.empty, Stream.empty[Task, Byte])) {
        case ((hsAcc, bsAcc), Right(byte)) => (hsAcc, bsAcc ++ emit(byte))
        case ((hsAcc, bsAcc), Left(hs)) => (hsAcc ++ hs, bsAcc)
      }}.unsafeRun()


      headers mustEqual (expectedHeaders)
      byteStream.runLog.attemptFold(e => Left(e), v => Right(v.foldLeft("")(_ + _))) mustEqual Right(expected)
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

      def unspool(str: String): Stream[Task, Byte] = emits(str getBytes "ASCII")

      val results: Stream[Task, Either[Headers, Byte]] = unspool(input) through MultipartParser.parse(boundary)

      val bytes = results.runLog.unsafeRun()

      val (headers, byteStream) = results.runLog.unsafeRun().foldLeft(Headers.empty, Stream.empty[Task, Byte]) {
        case ((hsAcc, bsAcc), Right(byte)) => (hsAcc, bsAcc ++ emit(byte))
        case ((hsAcc, bsAcc), Left(hs)) => (hsAcc ++ hs, bsAcc)
      }

      headers mustEqual (expectedHeaders)
      byteStream.runLog.attemptFold(e => Left(e), v => Right(v.foldLeft("")(_ + _))) mustEqual Right(expected)
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

      def unspool(str: String): Stream[Task, Byte] = emits(str getBytes "ASCII")

      val results: Stream[Task, Either[Headers, Byte]] = unspool(input) through MultipartParser.parse(boundary)

      val (headers, byteStream) = results.runLog.unsafeRun().foldLeft(Headers.empty, Stream.empty[Task, Byte]) {
        case ((hsAcc, bsAcc), Right(byte)) => (hsAcc, bsAcc ++ emit(byte))
        case ((hsAcc, bsAcc), Left(hs)) => (hsAcc ++ hs, bsAcc)
      }

      byteStream.runLog.attemptFold(e => Left(e), v => Right(v.foldLeft("")(_ + _))) mustEqual Right(expected)
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

      def unspool(str: String): Stream[Task, Byte] = emits(str getBytes "ASCII")
      val results: Stream[Task, Either[Headers, Byte]] = unspool(input) through MultipartParser.parse(boundary)

      results.runLog.unsafeRun() must throwAn[MalformedMessageBodyFailure]
    }
  }
}
