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

import cats.data.Chain
import cats.effect._
import cats.syntax.all._
import fs2.Stream._
import fs2._
import fs2.io.file.Files
import fs2.io.file.Path
import org.http4s.Status.Ok
import org.http4s.headers.`Content-Type`

import java.nio.charset.StandardCharsets
import java.util.Arrays

class EntityDecoderSuite extends Http4sSuite {
  val `application/excel`: MediaType =
    new MediaType("application", "excel", true, false, List("xls"))
  val `application/gnutar`: MediaType =
    new MediaType("application", "gnutar", true, false, List("tar"))
  val `application/soap+xml`: MediaType =
    new MediaType("application", "soap+xml", MediaType.Compressible, MediaType.NotBinary)
  val `text/x-h` = new MediaType("text", "x-h")

  def getBody(body: EntityBody[IO]): IO[Array[Byte]] =
    body.compile.toVector.map(_.toArray)

  def strBody(body: String): Stream[IO, Byte] =
    chunk(Chunk.array(body.getBytes(StandardCharsets.UTF_8)))

  private val req = Response[IO](Ok).withEntity("foo").pure[IO]

  test("flatMapR with success") {
    DecodeResult
      .success(req)
      .flatMap { r =>
        EntityDecoder
          .text[IO]
          .flatMapR(_ => DecodeResult.successT[IO, String]("bar"))
          .decode(r, strict = false)
      }
      .value
      .assertEquals(Right("bar"))
  }

  test("flatMapR with failure") {
    DecodeResult
      .success(req)
      .flatMap { r =>
        EntityDecoder
          .text[IO]
          .flatMapR(_ => DecodeResult.failureT[IO, String](MalformedMessageBodyFailure("bummer")))
          .decode(r, strict = false)
      }
      .value
      .assertEquals(Left(MalformedMessageBodyFailure("bummer")))
  }

  test("handleError from failure") {
    DecodeResult
      .success(req)
      .flatMap { r =>
        EntityDecoder
          .text[IO]
          .flatMapR(_ => DecodeResult.failureT[IO, String](MalformedMessageBodyFailure("bummer")))
          .handleError(_ => "SAVED")
          .decode(r, strict = false)
      }
      .value
      .assertEquals(Right("SAVED"))
  }

  test("handleErrorWith success from failure") {
    DecodeResult
      .success(req)
      .flatMap { r =>
        EntityDecoder
          .text[IO]
          .flatMapR(_ => DecodeResult.failureT[IO, String](MalformedMessageBodyFailure("bummer")))
          .handleErrorWith(_ => DecodeResult.successT[IO, String]("SAVED"))
          .decode(r, strict = false)
      }
      .value
      .assertEquals(Right("SAVED"))
  }

  test("recoverWith failure from failure") {
    DecodeResult
      .success(req)
      .flatMap { r =>
        EntityDecoder
          .text[IO]
          .flatMapR(_ => DecodeResult.failureT[IO, String](MalformedMessageBodyFailure("bummer")))
          .handleErrorWith(_ =>
            DecodeResult.failureT[IO, String](MalformedMessageBodyFailure("double bummer"))
          )
          .decode(r, strict = false)
      }
      .value
      .assertEquals(Left(MalformedMessageBodyFailure("double bummer")))
  }

  test("transform from success") {
    DecodeResult
      .success(req)
      .flatMap { r =>
        EntityDecoder
          .text[IO]
          .transform(_ => Right("TRANSFORMED"))
          .decode(r, strict = false)
      }
      .value
      .assertEquals(Right("TRANSFORMED"))
  }

  test("bimap from failure") {
    DecodeResult
      .success(req)
      .flatMap { r =>
        EntityDecoder
          .text[IO]
          .flatMapR(_ => DecodeResult.failureT[IO, String](MalformedMessageBodyFailure("bummer")))
          .bimap(_ => MalformedMessageBodyFailure("double bummer"), identity)
          .decode(r, strict = false)
      }
      .value
      .assertEquals(Left(MalformedMessageBodyFailure("double bummer")))
  }

  test("transformWith from success") {
    DecodeResult
      .success(req)
      .flatMap { r =>
        EntityDecoder
          .text[IO]
          .transformWith(_ => DecodeResult.successT[IO, String]("TRANSFORMED"))
          .decode(r, strict = false)
      }
      .value
      .assertEquals(Right("TRANSFORMED"))
  }

  test("biflatMap from failure") {
    DecodeResult
      .success(req)
      .flatMap { r =>
        EntityDecoder
          .text[IO]
          .flatMapR(_ => DecodeResult.failureT[IO, String](MalformedMessageBodyFailure("bummer")))
          .biflatMap(
            _ => DecodeResult.failureT[IO, String](MalformedMessageBodyFailure("double bummer")),
            s => DecodeResult.successT[IO, String](s),
          )
          .decode(r, strict = false)
      }
      .value
      .assertEquals(Left(MalformedMessageBodyFailure("double bummer")))
  }

  val nonMatchingDecoder: EntityDecoder[IO, String] =
    EntityDecoder.decodeBy(MediaRange.`video/*`) { _ =>
      DecodeResult.failureT(MalformedMessageBodyFailure("Nope."))
    }

  val decoder1: EntityDecoder[IO, Int] =
    EntityDecoder.decodeBy(`application/gnutar`) { _ =>
      DecodeResult.successT(1)
    }

  val decoder2: EntityDecoder[IO, Int] =
    EntityDecoder.decodeBy(`application/excel`) { _ =>
      DecodeResult.successT(2)
    }

  val failDecoder: EntityDecoder[IO, Int] =
    EntityDecoder.decodeBy(`application/soap+xml`) { _ =>
      DecodeResult.failureT(MalformedMessageBodyFailure("Nope."))
    }

  test("Check the validity of a message body") {
    val decoder = EntityDecoder.decodeBy[IO, String](MediaType.text.plain) { _ =>
      DecodeResult.failureT(InvalidMessageBodyFailure("Nope."))
    }

    decoder
      .decode(Request[IO](headers = Headers(`Content-Type`(MediaType.text.plain))), strict = true)
      .swap
      .map(_.toHttpResponse[IO](HttpVersion.`HTTP/1.1`))
      .map(_.status)
      .value
      .assertEquals(Right(Status.UnprocessableEntity))
  }

  test("Not match invalid media type") {
    assert(!nonMatchingDecoder.matchesMediaType(MediaType.text.plain))
  }

  test("Match valid media range") {
    assert(EntityDecoder.text[IO].matchesMediaType(MediaType.text.plain))
  }

  test("Match valid media type to a range") {
    assert(EntityDecoder.text[IO].matchesMediaType(MediaType.text.css))
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

  test("decodeStrict should produce a MediaTypeMissing if message has no content type") {
    val req = Request[IO]()
    decoder1
      .decode(req, strict = true)
      .value
      .assertEquals(Left(MediaTypeMissing(decoder1.consumes)))
  }

  test("decodeStrict should produce a MediaTypeMismatch if message has unsupported content type") {
    val tpe = MediaType.text.css
    val req = Request[IO](headers = Headers(`Content-Type`(tpe)))
    decoder1
      .decode(req, strict = true)
      .value
      .assertEquals(Left(MediaTypeMismatch(tpe, decoder1.consumes)))
  }

  test(
    "composing EntityDecoders with <+> A message with a MediaType that is not supported by any of the decoders will be attempted by the last decoder"
  ) {
    val reqMediaType = MediaType.application.`atom+xml`
    val req = Request[IO](headers = Headers(`Content-Type`(reqMediaType)))
    (decoder1 <+> decoder2).decode(req, strict = false).value.assertEquals(Right(2))
  }

  test(
    "composing EntityDecoders with <+> A catch all decoder will always attempt to decode a message"
  ) {
    val reqSomeOtherMediaType =
      Request[IO](headers = Headers(`Content-Type`(`text/x-h`)))
    val reqNoMediaType = Request[IO]()
    val catchAllDecoder: EntityDecoder[IO, Int] = EntityDecoder.decodeBy(MediaRange.`*/*`) { _ =>
      DecodeResult.successT(3)
    }
    (decoder1 <+> catchAllDecoder)
      .decode(reqSomeOtherMediaType, strict = true)
      .value
      .assertEquals(Right(3)) *>
      (catchAllDecoder <+> decoder1)
        .decode(reqSomeOtherMediaType, strict = true)
        .value
        .assertEquals(Right(3)) *>
      (catchAllDecoder <+> decoder1)
        .decode(reqNoMediaType, strict = true)
        .value
        .assertEquals(Right(3))
  }

  test(
    "composing EntityDecoders with <+>if decode is called with strict, will produce a MediaTypeMissing or MediaTypeMismatch with ALL supported media types of the composite decoder"
  ) {
    val reqMediaType = `text/x-h`
    val expectedMediaRanges = failDecoder.consumes ++ decoder1.consumes ++ decoder2.consumes
    val reqSomeOtherMediaType =
      Request[IO](headers = Headers(`Content-Type`(reqMediaType)))
    (decoder1 <+> decoder2 <+> failDecoder)
      .decode(reqSomeOtherMediaType, strict = true)
      .value
      .assertEquals(Left(MediaTypeMismatch(reqMediaType, expectedMediaRanges))) *>
      (decoder1 <+> decoder2 <+> failDecoder)
        .decode(Request(), strict = true)
        .value
        .assertEquals(Left(MediaTypeMissing(expectedMediaRanges)))
  }

  private val request = Request[IO]().withEntity("whatever")

  test("apply should invoke the function with the right on a success") {
    val happyDecoder: EntityDecoder[IO, String] =
      EntityDecoder.decodeBy(MediaRange.`*/*`)(_ => DecodeResult.success(IO.pure("hooray")))
    IO.async[String] { cb =>
      request
        .decodeWith(happyDecoder, strict = false) { s =>
          cb(Right(s))
          IO.pure(Response())
        }
        .as(None)
    }.assertEquals("hooray")
  }

  test("apply should wrap the ParseFailure in a ParseException on failure") {
    val grumpyDecoder: EntityDecoder[IO, String] = EntityDecoder.decodeBy(MediaRange.`*/*`)(_ =>
      DecodeResult.failure[IO, String](IO.pure(MalformedMessageBodyFailure("Bah!")))
    )
    request
      .decodeWith(grumpyDecoder, strict = false) { _ =>
        IO.pure(Response())
      }
      .map(_.status)
      .assertEquals(Status.BadRequest)
  }

  val server: Request[IO] => IO[Response[IO]] = { req =>
    req
      .decode[UrlForm](form => Response[IO](Ok).withEntity(form).pure[IO])
      .attempt
      .map {
        case Right(r) => r
        case Left(_) => Response(Status.BadRequest)
      }
  }

  test("application/x-www-form-urlencoded should Decode form encoded body") {
    val urlForm = UrlForm(
      Map(
        "Formula" -> Chain("a <+ b == 13%!"),
        "Age" -> Chain("23"),
        "Name" -> Chain("Jonathan Doe"),
      )
    )
    val resp: IO[Response[IO]] = Request[IO]()
      .withEntity(urlForm)(UrlForm.entityEncoder(Charset.`UTF-8`))
      .pure[IO]
      .flatMap(server)
    resp.map(_.status).assertEquals(Ok) *>
      DecodeResult
        .success(resp)
        .flatMap(UrlForm.entityDecoder[IO].decode(_, strict = true))
        .value
        .assertEquals(Right(urlForm))
  }

  // TODO: need to make urlDecode strict
  // test("application/x-www-form-urlencoded should handle a parse failure") {
  //   server(Request(body = strBody("%C"))).map(_.status) must be(Status.BadRequest)
  // }.pendingUntilFixed

  val binData: Array[Byte] = "Bytes 10111".getBytes

  def readFile(in: Path): IO[Array[Byte]] =
    Files[IO].readAll(in).chunks.compile.foldMonoid.map(_.toArray)

  def readTextFile(in: Path): IO[String] =
    Files[IO].readAll(in).through(fs2.text.utf8.decode).compile.foldMonoid

  private def mockServe(req: Request[IO])(route: Request[IO] => IO[Response[IO]]) =
    route(req.withBodyStream(chunk(Chunk.array(binData))))

  test("A File EntityDecoder should write a text file from a byte string") {
    Files[IO]
      .tempFile(None, "foo", "bar", None)
      .use { tmpFile =>
        val response = mockServe(Request()) { req =>
          req.decodeWith(EntityDecoder.textFile(tmpFile), strict = false) { _ =>
            Response[IO](Ok).withEntity("Hello").pure[IO]
          }
        }
        response.flatMap { response =>
          assertEquals(response.status, Status.Ok)
          readTextFile(tmpFile).assertEquals(new String(binData)) *>
            response.as[String].assertEquals("Hello")
        }
      }
  }

  test("A File EntityDecoder should write a binary file from a byte string") {
    Files[IO]
      .tempFile(None, "foo", "bar", None)
      .use { tmpFile =>
        val response = mockServe(Request()) { case req =>
          req.decodeWith(EntityDecoder.binFile(tmpFile), strict = false) { _ =>
            Response[IO](Ok).withEntity("Hello").pure[IO]
          }
        }

        response.flatMap { response =>
          assertEquals(response.status, Status.Ok)
          response.body.compile.toVector
            .map(_.toArray)
            .map(Arrays.equals(_, "Hello".getBytes))
            .assert *>
            readFile(tmpFile).map(Arrays.equals(_, binData)).assert
        }
      }
  }

  test("binary EntityDecoder should yield an empty array on a bodyless message") {
    val msg = Request[IO]()
    EntityDecoder
      .binary[IO]
      .decode(msg, strict = false)
      .value
      .assertEquals(Right(Chunk.empty[Byte]))
  }

  test("binary EntityDecoder should concat Chunks") {
    val d1 = Array[Byte](1, 2, 3); val d2 = Array[Byte](4, 5, 6)
    val body = chunk(Chunk.array(d1)) ++ chunk(Chunk.array(d2))
    val msg = Request[IO](body = body)
    val expected = Chunk.array(Array[Byte](1, 2, 3, 4, 5, 6))
    EntityDecoder.binary[IO].decode(msg, strict = false).value.assertEquals(Right(expected))
  }

  test("binary EntityDecoder should Match any media type") {
    assert(EntityDecoder.binary[IO].matchesMediaType(MediaType.text.plain))
  }

  val str = "Oekraïene"
  test("decodeText should Use an charset defined by the Content-Type header") {
    val resp = Response[IO](Ok)
      .withEntity(str.getBytes(Charset.`UTF-8`.nioCharset))
      .withContentType(`Content-Type`(MediaType.text.plain, Some(Charset.`UTF-8`)))
    EntityDecoder.decodeText(resp)(implicitly, Charset.`US-ASCII`).assertEquals(str)
  }

  test("decodeText should Use the default if the Content-Type header does not define one") {
    val resp = Response[IO](Ok)
      .withEntity(str.getBytes(Charset.`UTF-8`.nioCharset))
      .withContentType(`Content-Type`(MediaType.text.plain, None))
    EntityDecoder.decodeText(resp)(implicitly, Charset.`UTF-8`).assertEquals(str)
  }

  // we want to return a specific kind of error when there is a MessageFailure
  sealed case class ErrorJson(value: String)
  implicit val errorJsonEntityEncoder: EntityEncoder[IO, ErrorJson] =
    EntityEncoder.simple[IO, ErrorJson](`Content-Type`(MediaType.application.json))(json =>
      Chunk.array(json.value.getBytes())
    )

// TODO: These won't work without an Eq for (Message[IO], Boolean) => DecodeResult[IO, A]
//  {
//    implicit def entityDecoderEq[A: Eq]: Eq[EntityDecoder[IO, A]] =
//      Eq.by[EntityDecoder[IO, A], (Message[IO], Boolean) => DecodeResult[IO, A]](_.decode)
//
//    checkAll(
//      "SemigroupK[EntityDecoder[IO, *]]",
//      SemigroupKTests[EntityDecoder[IO, *]]
//        .semigroupK[String])(Parameters(minTestsOk = 20, maxSize = 10))
//  }
}
