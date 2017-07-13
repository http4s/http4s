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
        case ((hsAcc, bvAcc), Left(hs)) => (hsAcc ++ hs, ByteVector.empty)
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

      val results: Stream[Task, Either[Headers,Byte]] = unspool(input).through(MultipartParser.parse(boundary))

      results.runLog.unsafeRun() must throwAn[MalformedMessageBodyFailure]
    }
  }
/*
  "receiveLine" should {
    "output an individual line with a large buffer" in {

      val unprocessedInput =
        """I Am A Yellow Monkey
          |Second Line""".stripMargin

      val input = ruinDelims(unprocessedInput)

      val results: Stream[Task, Either[ByteVector, MultipartParser.Out[ByteVector]]] =
        unspool(input)
          .mapChunks(chunk => Chunk.singleton(MultipartParser.chunkToBV(chunk)))
          .open
          .flatMap(MultipartParser.receiveLine(None))
          .close

      val agg = results.runLog.unsafeRun()
      val out = agg.collect{ case Right(MultipartParser.Out(bv, _)) => bv}.map{_.decodeAscii}.collect{ case Right(v) => v}
      val tail = agg.collect{ case Right(MultipartParser.Out(_, tail)) => tail}.map{_.map(_.decodeAscii)}.collect{ case Some(Right(v)) => v}
      val left = agg.collect{ case Left(bv) => bv}.map{_.decodeAscii}.collect{ case Right(v) => v}

      println(s"Out ByteVectors- $out")
      println(s"Out Tail ByteVectors- $tail")
      println(s"Left Bytevectors - $left")

      out mustEqual Vector("I Am A Yellow Monkey")
      tail mustEqual Vector("Second Line")
      left mustEqual Vector()
    }
  }

  "receiveCollapsedLine" should {
    "output a single line across small inputs" in {
      val unprocessedInput =
        """I Am A Yellow Monkey
          |Second Line""".stripMargin

      val input = ruinDelims(unprocessedInput)

      val results: Stream[Task, MultipartParser.Out[ByteVector]] =
        unspool(input,2)
          .mapChunks(chunk => Chunk.singleton(MultipartParser.chunkToBV(chunk)))
          .covary[Task]
          .open
          .flatMap(MultipartParser.receiveCollapsedLine(None))
          .close

      val agg = results.runLog.unsafeRun()
      val out = agg.collect{ case MultipartParser.Out(bv, _) => bv}.map{_.decodeAscii}.collect{ case Right(v) => v}
      val tail = agg.collect{ case MultipartParser.Out(_, t) => t}.map{_.map(_.decodeAscii)}.collect{ case Some(Right(v)) => v}

      println(s"Out ByteVectors- $out")
      println(s"Out Tail ByteVectors- $tail")

      out mustEqual Vector("I Am A Yellow Monkey")
    }
  }
*/

}
