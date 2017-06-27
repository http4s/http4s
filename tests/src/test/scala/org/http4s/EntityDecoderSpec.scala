package org.http4s

import java.io.{File, FileInputStream, InputStreamReader}
import java.nio.charset.StandardCharsets

import cats._
import cats.implicits._
import cats.effect.IO
import fs2.Stream._
import fs2._
import org.http4s.Status.Ok
import org.http4s.headers.`Content-Type`
import org.http4s.util.TrampolineExecutionContext
import org.specs2.execute.PendingUntilFixed

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

class EntityDecoderSpec extends Http4sSpec with PendingUntilFixed {
  implicit val executionContext: ExecutionContext = TrampolineExecutionContext

  def getBody(body: EntityBody[IO]): IO[Array[Byte]] =
    body.runLog.map(_.toArray)

  def strBody(body: String): Stream[IO, Byte] =
    chunk(Chunk.bytes(body.getBytes(StandardCharsets.UTF_8)))

  "EntityDecoder" can {
    val req = Response[IO](Ok).withBody("foo")
    "flatMapR with success" in {
      DecodeResult.success(req).flatMap { r =>
        EntityDecoder
          .text[IO]
          .flatMapR(_ => DecodeResult.success[IO, String]("bar"))
          .decode(r, strict = false)
      } must returnRight("bar")
    }

    "flatMapR with failure" in {
      DecodeResult.success(req).flatMap { r =>
        EntityDecoder
          .text[IO]
          .flatMapR(_ => DecodeResult.failure[IO, String](MalformedMessageBodyFailure("bummer")))
          .decode(r, strict = false)
      } must returnLeft(MalformedMessageBodyFailure("bummer"))
    }

    val nonMatchingDecoder: EntityDecoder[IO, String] =
      EntityDecoder.decodeBy(MediaRange.`video/*`) { _ =>
        DecodeResult.failure(MalformedMessageBodyFailure("Nope."))
      }

    val decoder1: EntityDecoder[IO, Int] =
      EntityDecoder.decodeBy(MediaType.`application/gnutar`) { _ =>
        DecodeResult.success(1)
      }

    val decoder2: EntityDecoder[IO, Int] =
      EntityDecoder.decodeBy(MediaType.`application/excel`) { _ =>
        DecodeResult.success(2)
      }

    val failDecoder: EntityDecoder[IO, Int] =
      EntityDecoder.decodeBy(MediaType.`application/soap+xml`) { _ =>
        DecodeResult.failure(MalformedMessageBodyFailure("Nope."))
      }

    "Check the validity of a message body" in {
      val decoder = EntityDecoder.decodeBy[IO, String](MediaType.`text/plain`) { _ =>
        DecodeResult.failure(InvalidMessageBodyFailure("Nope."))
      }

      decoder
        .decode(Request[IO](headers = Headers(`Content-Type`(MediaType.`text/plain`))), strict = true)
        .swap
        .semiflatMap(_.toHttpResponse[IO](HttpVersion.`HTTP/1.1`)) must returnRight(haveStatus(Status.UnprocessableEntity))
    }

    "Not match invalid media type" in {
      nonMatchingDecoder.matchesMediaType(MediaType.`text/plain`) must_== false
    }

    "Match valid media range" in {
      EntityDecoder.text[IO].matchesMediaType(MediaType.`text/plain`) must_== true
    }

    "Match valid media type to a range" in {
      EntityDecoder.text[IO].matchesMediaType(MediaType.`text/css`) must_== true
    }

    /* TODO: Parameterization
    "Completely customize the response of a ParsingFailure" in {
      val failure = GenericParsingFailure(
        "sanitized",
        "details",
        response = (httpVersion: HttpVersion) =>
          Response(Status.BadRequest, httpVersion).withBody(ErrorJson("""{"error":"parse error"}""")))
        .toHttpResponse(HttpVersion.`HTTP/1.1`)

      "the content type is application/json" ==> {
        failure must returnValue(haveMediaType(MediaType.`application/json`))
      }
    }

    "Completely customize the response of a DecodeFailure" in {
      val failure = GenericDecodeFailure(
        "unsupported media type: application/xyz because it's Sunday",
        response =
          (httpVersion: HttpVersion) => Response(Status.UnsupportedMediaType, httpVersion).withBody("not on a Sunday")
      )

      failure.toHttpResponse(HttpVersion.`HTTP/1.1`).as[String] must returnValue("not on a Sunday")
    }

    "Completely customize the response of a MessageBodyFailure" in {
      // customized decoder, with a custom response
      val decoder = EntityDecoder.decodeBy[String](MediaType.`text/plain`) { msg =>
        DecodeResult.failure {
          val invalid = InvalidMessageBodyFailure("Nope.")
          GenericMessageBodyFailure(
            invalid.message,
            invalid.cause,
            (httpVersion: HttpVersion) =>
              Response(Status.UnprocessableEntity, httpVersion).withBody(ErrorJson("""{"error":"unprocessable"}"""))
          )
        }
      }

      val decoded = decoder
        .decode(Request().replaceAllHeaders(`Content-Type`(MediaType.`text/plain`)), strict = true)
        .swap
        .semiflatMap(_.toHttpResponse(HttpVersion.`HTTP/1.1`))

      "the content type is application/json instead of text/plain" ==> {
        decoded must returnRight(haveMediaType(MediaType.`application/json`))
      }
    }
    */

    "decodeStrict" >> {
      "should produce a MediaTypeMissing if message has no content type" in {
        val req = Request[IO]()
        decoder1.decode(req, strict = true) must returnLeft(MediaTypeMissing(decoder1.consumes))
      }
      "should produce a MediaTypeMismatch if message has unsupported content type" in {
        val tpe = MediaType.`text/css`
        val req = Request[IO](headers = Headers(`Content-Type`(tpe)))
        decoder1.decode(req, strict = true) must returnLeft(MediaTypeMismatch(tpe, decoder1.consumes))
      }
    }

    "composing EntityDecoders with orElse" >> {
      "A message with a MediaType that is not supported by any of the decoders" +
        " will be attempted by the last decoder" in {
        val reqMediaType = MediaType.`application/atom+xml`
        val req          = Request[IO](headers = Headers(`Content-Type`(reqMediaType)))
        (decoder1 orElse decoder2).decode(req, strict = false) must returnRight(2)
      }
      "A catch all decoder will always attempt to decode a message" in {
        val reqSomeOtherMediaType = Request[IO](headers = Headers(`Content-Type`(MediaType.`text/x-h`)))
        val reqNoMediaType        = Request[IO]()
        val catchAllDecoder: EntityDecoder[IO, Int] = EntityDecoder.decodeBy(MediaRange.`*/*`) { msg =>
          DecodeResult.success(3)
        }
        (decoder1 orElse catchAllDecoder).decode(reqSomeOtherMediaType, strict = true) must returnRight(3)
        (catchAllDecoder orElse decoder1).decode(reqSomeOtherMediaType, strict = true) must returnRight(3)
        (catchAllDecoder orElse decoder1).decode(reqNoMediaType, strict = true) must returnRight(3)
      }
      "if decode is called with strict, will produce a MediaTypeMissing or MediaTypeMismatch " +
        "with ALL supported media types of the composite decoder" in {
        val reqMediaType          = MediaType.`text/x-h`
        val expectedMediaRanges   = decoder1.consumes ++ decoder2.consumes ++ failDecoder.consumes
        val reqSomeOtherMediaType = Request[IO](headers = Headers(`Content-Type`(reqMediaType)))
        (decoder1 orElse decoder2 orElse failDecoder).decode(reqSomeOtherMediaType, strict = true) must returnLeft(
          MediaTypeMismatch(reqMediaType, expectedMediaRanges))
        (decoder1 orElse decoder2 orElse failDecoder).decode(Request(), strict = true) must returnLeft(
          MediaTypeMissing(expectedMediaRanges))
      }
    }
  }

  "apply" should {
    val request = Request[IO]().withBody("whatever")

    "invoke the function with  the right on a success" in {
      val happyDecoder: EntityDecoder[IO, String] = EntityDecoder.decodeBy(MediaRange.`*/*`)(_ => DecodeResult.success(IO.pure("hooray")))
      IO.async[String] { cb =>
        request
          .decodeWith(happyDecoder, strict = false) { s =>
            cb(Right(s))
            IO.pure(Response())
          }
          .unsafeRunSync
        ()
      } must returnValue("hooray")
    }

    "wrap the ParseFailure in a ParseException on failure" in {
      val grumpyDecoder: EntityDecoder[IO, String] = EntityDecoder.decodeBy(MediaRange.`*/*`)(_ =>
        DecodeResult.failure[IO, String](IO.pure(MalformedMessageBodyFailure("Bah!"))))
      request.decodeWith(grumpyDecoder, strict = false) { _ =>
        IO.pure(Response())
      } must returnValue(haveStatus(Status.BadRequest))
    }
  }

  "application/x-www-form-urlencoded" should {

    val server: Request[IO] => IO[Response[IO]] = { req =>
      req
        .decode[UrlForm](form => Response(Ok).withBody(form))
        .attempt.map((e: Either[Throwable, Response[IO]]) => e.getOrElse(Response(Status.BadRequest)))
    }

    "Decode form encoded body" in {
      val urlForm = UrlForm(
        Map(
          "Formula" -> Seq("a + b == 13%!"),
          "Age"     -> Seq("23"),
          "Name"    -> Seq("Jonathan Doe")
        ))
      val resp: IO[Response[IO]] = Request().withBody(urlForm)(Functor[IO], UrlForm.entityEncoder(Applicative[IO], Charset.`UTF-8`)).flatMap(server)
      resp must returnValue(haveStatus(Ok))
      DecodeResult.success(resp).flatMap(UrlForm.entityDecoder[IO].decode(_, strict = true)) must returnRight(urlForm)
    }

    // TODO: need to make urlDecode strict
    "handle a parse failure" in {
      server(Request(body = strBody("%C"))).map(_.status) must be(Status.BadRequest)
    }.pendingUntilFixed
  }

  "A File EntityDecoder" should {
    val binData: Array[Byte] = "Bytes 10111".getBytes

    def readFile(in: File): Array[Byte] = {
      val os   = new FileInputStream(in)
      val data = new Array[Byte](in.length.asInstanceOf[Int])
      os.read(data)
      data
    }

    def readTextFile(in: File): String = {
      val os   = new InputStreamReader(new FileInputStream(in))
      val data = new Array[Char](in.length.asInstanceOf[Int])
      os.read(data, 0, in.length.asInstanceOf[Int])
      data.foldLeft("")(_ + _)
    }

    def mockServe(req: Request[IO])(route: Request[IO] => IO[Response[IO]]) =
      route(req.copy(body = chunk(Chunk.bytes(binData))))

    "Write a text file from a byte string" in {
      val tmpFile = File.createTempFile("foo", "bar")
      try {
        val response = mockServe(Request()) { req =>
            req.decodeWith(textFile(tmpFile), strict = false) { _ =>
              Response(Ok).withBody("Hello")
            }
        }.unsafeRunSync

        readTextFile(tmpFile) must_== (new String(binData))
        response.status must_== (Status.Ok)
        getBody(response.body) must returnValue("Hello".getBytes)
      } finally {
        tmpFile.delete()
        ()
      }
    }

    "Write a binary file from a byte string" in {
      val tmpFile = File.createTempFile("foo", "bar")
      try {
        val response = mockServe(Request()) {
          case req =>
            req.decodeWith(binFile(tmpFile), strict = false) { _ =>
              Response(Ok).withBody("Hello")
            }
        }.unsafeRunSync

        response must beStatus(Status.Ok)
        getBody(response.body) must returnValue("Hello".getBytes)
        readFile(tmpFile) must_== (binData)
      } finally {
        tmpFile.delete()
        ()
      }
    }
  }

  "binary EntityDecoder" should {
    "yield an empty array on a bodyless message" in {
      val msg = Request[IO]()
      binary[IO].decode(msg, strict = false) must returnRight(Chunk.empty[Byte])
    }

    "concat ByteVectors" in {
      val d1   = Array[Byte](1, 2, 3); val d2 = Array[Byte](4, 5, 6)
      val body = chunk(Chunk.bytes(d1)) ++ chunk(Chunk.bytes(d2))
      val msg  = Request[IO](body = body)

      val expected = Chunk.bytes(Array[Byte](1, 2, 3, 4, 5, 6))
      binary[IO].decode(msg, strict = false) must returnRight(expected)
    }

    "Match any media type" in {
      binary[IO].matchesMediaType(MediaType.`text/plain`) must_== true
    }
  }

  "decodeString" should {
    val str = "Oekraïene"
    "Use an charset defined by the Content-Type header" in {
      Response[IO](Ok)
        .withBody(str.getBytes(Charset.`UTF-8`.nioCharset))
        .withContentType(Some(`Content-Type`(MediaType.`text/plain`, Some(Charset.`UTF-8`))))
        .flatMap(EntityDecoder.decodeString(_)(implicitly, Charset.`US-ASCII`)) must returnValue(str)
    }

    "Use the default if the Content-Type header does not define one" in {
      Response[IO](Ok)
        .withBody(str.getBytes(Charset.`UTF-8`.nioCharset))
        .withContentType(Some(`Content-Type`(MediaType.`text/plain`, None)))
        .flatMap(EntityDecoder.decodeString(_)(implicitly, Charset.`UTF-8`)) must returnValue(str)
    }
  }

  // we want to return a specific kind of error when there is a MessageFailure
  sealed case class ErrorJson(value: String)
  implicit val errorJsonEntityEncoder: EntityEncoder[IO, ErrorJson] =
    EntityEncoder.simple[IO, ErrorJson](`Content-Type`(MediaType.`application/json`))(json =>
      Chunk.bytes(json.value.getBytes()))

}
