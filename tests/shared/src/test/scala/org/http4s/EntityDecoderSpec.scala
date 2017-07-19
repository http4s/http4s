package org.http4s

import cats.Eq
import cats.effect._
import cats.effect.laws.discipline.arbitrary._
import cats.effect.laws.util.TestContext
import cats.effect.laws.util.TestInstances._
import cats.implicits._
import cats.laws.discipline.SemigroupKTests
import cats.laws.discipline.arbitrary._
import cats.laws.discipline.eq._
import fs2._
import fs2.Stream._
import java.io.{File, FileInputStream, InputStreamReader}
import java.nio.charset.StandardCharsets
import cats.data.Chain
import org.http4s.Status.Ok
import org.http4s.testing._
import org.http4s.headers.`Content-Type`
import org.http4s.util.execution.trampoline
import org.specs2.execute.PendingUntilFixed
import org.specs2.scalacheck.Parameters
import scala.concurrent.ExecutionContext

class EntityDecoderSpec extends Http4sSpec with PendingUntilFixed {
  implicit val executionContext: ExecutionContext = trampoline
  implicit val testContext: TestContext = TestContext()

  implicit def entityDecoderEq[A: Eq]: Eq[EntityDecoder[IO, A]] =
    Eq.by[EntityDecoder[IO, A], (Message[IO], Boolean) => DecodeResult[IO, A]](_.decode)

  def getBody(body: EntityBody[IO]): IO[Array[Byte]] =
    body.compile.toVector.map(_.toArray)

  def strBody(body: String): Stream[IO, Byte] =
    chunk(Chunk.bytes(body.getBytes(StandardCharsets.UTF_8)))

  "EntityDecoder".can {
    val req = Response[IO](Ok).withEntity("foo").pure[IO]
    "flatMapR with success" in {
      DecodeResult.success(req).flatMap { r =>
        EntityDecoder
          .text[IO]
          .flatMapR(_ => DecodeResult.success[IO, String]("bar"))
          .decode(r, strict = false)
      } must returnRight("bar")
    }

    "flatMapR with failure" in {
      DecodeResult
        .success(req)
        .flatMap { r =>
          EntityDecoder
            .text[IO]
            .flatMapR(_ => DecodeResult.failure[IO, String](MalformedMessageBodyFailure("bummer")))
            .decode(r, strict = false)
        }
        .value must returnValue(Left(MalformedMessageBodyFailure("bummer")))
    }

    "handleError from failure" in {
      DecodeResult
        .success(req)
        .flatMap { r =>
          EntityDecoder
            .text[IO]
            .flatMapR(_ => DecodeResult.failure[IO, String](MalformedMessageBodyFailure("bummer")))
            .handleError(_ => "SAVED")
            .decode(r, strict = false)
        } must returnRight("SAVED")
    }

    "handleErrorWith success from failure" in {
      DecodeResult
        .success(req)
        .flatMap { r =>
          EntityDecoder
            .text[IO]
            .flatMapR(_ => DecodeResult.failure[IO, String](MalformedMessageBodyFailure("bummer")))
            .handleErrorWith(_ => DecodeResult.success("SAVED"))
            .decode(r, strict = false)
        } must returnRight("SAVED")
    }

    "recoverWith failure from failure" in {
      DecodeResult
        .success(req)
        .flatMap { r =>
          EntityDecoder
            .text[IO]
            .flatMapR(_ => DecodeResult.failure[IO, String](MalformedMessageBodyFailure("bummer")))
            .handleErrorWith(_ =>
              DecodeResult.failure[IO, String](MalformedMessageBodyFailure("double bummer")))
            .decode(r, strict = false)
        }
        .value must returnValue(Left(MalformedMessageBodyFailure("double bummer")))
    }

    "transform from success" in {
      DecodeResult
        .success(req)
        .flatMap { r =>
          EntityDecoder
            .text[IO]
            .transform(_ => Right("TRANSFORMED"))
            .decode(r, strict = false)
        } must returnRight("TRANSFORMED")
    }

    "bimap from failure" in {
      DecodeResult
        .success(req)
        .flatMap { r =>
          EntityDecoder
            .text[IO]
            .flatMapR(_ => DecodeResult.failure[IO, String](MalformedMessageBodyFailure("bummer")))
            .bimap(_ => MalformedMessageBodyFailure("double bummer"), identity)
            .decode(r, strict = false)
        }
        .value must returnValue(Left(MalformedMessageBodyFailure("double bummer")))
    }

    "transformWith from success" in {
      DecodeResult
        .success(req)
        .flatMap { r =>
          EntityDecoder
            .text[IO]
            .transformWith(_ => DecodeResult.success[IO, String]("TRANSFORMED"))
            .decode(r, strict = false)
        } must returnRight("TRANSFORMED")
    }

    "biflatMap from failure" in {
      DecodeResult
        .success(req)
        .flatMap { r =>
          EntityDecoder
            .text[IO]
            .flatMapR(_ => DecodeResult.failure[IO, String](MalformedMessageBodyFailure("bummer")))
            .biflatMap(
              _ => DecodeResult.failure[IO, String](MalformedMessageBodyFailure("double bummer")),
              s => DecodeResult.success(s)
            )
            .decode(r, strict = false)
        }
        .value must returnValue(Left(MalformedMessageBodyFailure("double bummer")))
    }

    val nonMatchingDecoder: EntityDecoder[IO, String] =
      EntityDecoder.decodeBy(MediaRange.`video/*`) { _ =>
        DecodeResult.failure(MalformedMessageBodyFailure("Nope."))
      }

    val decoder1: EntityDecoder[IO, Int] =
      EntityDecoder.decodeBy(`application/gnutar`) { _ =>
        DecodeResult.success(1)
      }

    val decoder2: EntityDecoder[IO, Int] =
      EntityDecoder.decodeBy(`application/excel`) { _ =>
        DecodeResult.success(2)
      }

    val failDecoder: EntityDecoder[IO, Int] =
      EntityDecoder.decodeBy(`application/soap+xml`) { _ =>
        DecodeResult.failure(MalformedMessageBodyFailure("Nope."))
      }

    "Check the validity of a message body" in {
      val decoder = EntityDecoder.decodeBy[IO, String](MediaType.text.plain) { _ =>
        DecodeResult.failure(InvalidMessageBodyFailure("Nope."))
      }

      decoder
        .decode(Request[IO](headers = Headers(`Content-Type`(MediaType.text.plain))), strict = true)
        .swap
        .semiflatMap(_.toHttpResponse[IO](HttpVersion.`HTTP/1.1`)) must returnRight(
        haveStatus(Status.UnprocessableEntity))
    }

    "Not match invalid media type" in {
      nonMatchingDecoder.matchesMediaType(MediaType.text.plain) must_== false
    }

    "Match valid media range" in {
      EntityDecoder.text[IO].matchesMediaType(MediaType.text.plain) must_== true
    }

    "Match valid media type to a range" in {
      EntityDecoder.text[IO].matchesMediaType(MediaType.text.css) must_== true
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
      val decoder = EntityDecoder.decodeBy[String](MediaType.text.plain) { msg =>
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
        .decode(Request().withHeaders(`Content-Type`(MediaType.text.plain)), strict = true)
        .swap
        .semiflatMap(_.toHttpResponse(HttpVersion.`HTTP/1.1`))

      the content type is application/json instead of plain ==> {
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
        val tpe = MediaType.text.css
        val req = Request[IO](headers = Headers(`Content-Type`(tpe)))
        decoder1.decode(req, strict = true) must returnLeft(
          MediaTypeMismatch(tpe, decoder1.consumes))
      }
    }

    "composing EntityDecoders with <+>" >> {
      "A message with a MediaType that is not supported by any of the decoders" +
        " will be attempted by the last decoder" in {
        val reqMediaType = MediaType.application.`atom+xml`
        val req = Request[IO](headers = Headers(`Content-Type`(reqMediaType)))
        (decoder1 <+> decoder2).decode(req, strict = false) must returnRight(2)
      }
      "A catch all decoder will always attempt to decode a message" in {
        val reqSomeOtherMediaType =
          Request[IO](headers = Headers(`Content-Type`(`text/x-h`)))
        val reqNoMediaType = Request[IO]()
        val catchAllDecoder: EntityDecoder[IO, Int] = EntityDecoder.decodeBy(MediaRange.`*/*`) {
          msg =>
            DecodeResult.success(3)
        }
        (decoder1 <+> catchAllDecoder)
          .decode(reqSomeOtherMediaType, strict = true) must returnRight(3)
        (catchAllDecoder <+> decoder1)
          .decode(reqSomeOtherMediaType, strict = true) must returnRight(3)
        (catchAllDecoder <+> decoder1).decode(reqNoMediaType, strict = true) must returnRight(3)
      }
      "if decode is called with strict, will produce a MediaTypeMissing or MediaTypeMismatch " +
        "with ALL supported media types of the composite decoder" in {
        val reqMediaType = `text/x-h`
        val expectedMediaRanges = failDecoder.consumes ++ decoder1.consumes ++ decoder2.consumes
        val reqSomeOtherMediaType = Request[IO](headers = Headers(`Content-Type`(reqMediaType)))
        (decoder1 <+> decoder2 <+> failDecoder)
          .decode(reqSomeOtherMediaType, strict = true) must returnLeft(
          MediaTypeMismatch(reqMediaType, expectedMediaRanges))

        (decoder1 <+> decoder2 <+> failDecoder).decode(Request(), strict = true) must returnLeft(
          MediaTypeMissing(expectedMediaRanges))
      }
    }
  }

  "apply" should {
    val request = Request[IO]().withEntity("whatever")

    "invoke the function with  the right on a success" in {
      val happyDecoder: EntityDecoder[IO, String] =
        EntityDecoder.decodeBy(MediaRange.`*/*`)(_ => DecodeResult.success(IO.pure("hooray")))
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
        .decode[UrlForm](form => Response[IO](Ok).withEntity(form).pure[IO])
        .attempt
        .map((e: Either[Throwable, Response[IO]]) => e.right.getOrElse(Response(Status.BadRequest)))
    }

    "Decode form encoded body" in {
      val urlForm = UrlForm(
        Map(
          "Formula" -> Chain("a + b == 13%!"),
          "Age" -> Chain("23"),
          "Name" -> Chain("Jonathan Doe")
        ))
      val resp: IO[Response[IO]] = Request[IO]()
        .withEntity(urlForm)(UrlForm.entityEncoder(Charset.`UTF-8`))
        .pure[IO]
        .flatMap(server)
      resp must returnValue(haveStatus(Ok))
      DecodeResult
        .success(resp)
        .flatMap(UrlForm.entityDecoder[IO].decode(_, strict = true)) must returnRight(urlForm)
    }

    // TODO: need to make urlDecode strict
    "handle a parse failure" in {
      server(Request(body = strBody("%C"))).map(_.status) must be(Status.BadRequest)
    }.pendingUntilFixed
  }

  "A File EntityDecoder" should {
    val binData: Array[Byte] = "Bytes 10111".getBytes

    def readFile(in: File): Array[Byte] = {
      val os = new FileInputStream(in)
      val data = new Array[Byte](in.length.asInstanceOf[Int])
      os.read(data)
      data
    }

    def readTextFile(in: File): String = {
      val os = new InputStreamReader(new FileInputStream(in))
      val data = new Array[Char](in.length.asInstanceOf[Int])
      os.read(data, 0, in.length.asInstanceOf[Int])
      data.foldLeft("")(_ + _)
    }

    def mockServe(req: Request[IO])(route: Request[IO] => IO[Response[IO]]) =
      route(req.withBodyStream(chunk(Chunk.bytes(binData))))

    "Write a text file from a byte string" in {
      val tmpFile = File.createTempFile("foo", "bar")
      try {
        val response = mockServe(Request()) { req =>
          req.decodeWith(
            EntityDecoder.textFile(tmpFile, testBlockingExecutionContext),
            strict = false) { _ =>
            Response[IO](Ok).withEntity("Hello").pure[IO]
          }
        }.unsafeRunSync

        readTextFile(tmpFile) must_== new String(binData)
        response.status must_== Status.Ok
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
            req.decodeWith(
              EntityDecoder.binFile(tmpFile, testBlockingExecutionContext),
              strict = false) { _ =>
              Response[IO](Ok).withEntity("Hello").pure[IO]
            }
        }.unsafeRunSync

        response must beStatus(Status.Ok)
        getBody(response.body) must returnValue("Hello".getBytes)
        readFile(tmpFile) must_== binData
      } finally {
        tmpFile.delete()
        ()
      }
    }
  }

  "binary EntityDecoder" should {
    "yield an empty array on a bodyless message" in {
      val msg = Request[IO]()
      EntityDecoder.binary[IO].decode(msg, strict = false) must returnRight(Chunk.empty[Byte])
    }

    "concat Chunks" in {
      val d1 = Array[Byte](1, 2, 3); val d2 = Array[Byte](4, 5, 6)
      val body = chunk(Chunk.bytes(d1)) ++ chunk(Chunk.bytes(d2))
      val msg = Request[IO](body = body)
      val expected = Chunk.bytes(Array[Byte](1, 2, 3, 4, 5, 6))
      EntityDecoder.binary[IO].decode(msg, strict = false) must returnRight(expected)
    }

    "Match any media type" in {
      EntityDecoder.binary[IO].matchesMediaType(MediaType.text.plain) must_== true
    }
  }

  "decodeString" should {
    val str = "Oekraïene"
    "Use an charset defined by the Content-Type header" in {
      val resp = Response[IO](Ok)
        .withEntity(str.getBytes(Charset.`UTF-8`.nioCharset))
        .withContentType(`Content-Type`(MediaType.text.plain, Some(Charset.`UTF-8`)))
      EntityDecoder.decodeString(resp)(implicitly, Charset.`US-ASCII`) must returnValue(str)
    }

    "Use the default if the Content-Type header does not define one" in {
      val resp = Response[IO](Ok)
        .withEntity(str.getBytes(Charset.`UTF-8`.nioCharset))
        .withContentType(`Content-Type`(MediaType.text.plain, None))
      EntityDecoder.decodeString(resp)(implicitly, Charset.`UTF-8`) must returnValue(str)
    }
  }

  // we want to return a specific kind of error when there is a MessageFailure
  sealed case class ErrorJson(value: String)
  implicit val errorJsonEntityEncoder: EntityEncoder[IO, ErrorJson] =
    EntityEncoder.simple[IO, ErrorJson](`Content-Type`(MediaType.application.json))(json =>
      Chunk.bytes(json.value.getBytes()))

  checkAll(
    "SemigroupK[EntityDecoder[IO, ?]]",
    SemigroupKTests[EntityDecoder[IO, ?]]
      .semigroupK[String])(Parameters(minTestsOk = 20, maxSize = 10))
}
