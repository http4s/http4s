package org.http4s

import org.http4s.Status.Ok
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.PendingUntilFixed
import scodec.bits.ByteVector
import org.http4s.headers.`Content-Type`

import java.io.{FileInputStream,File,InputStreamReader}

import scala.language.postfixOps
import scala.util.control.NonFatal
import scalaz.{-\/, \/-}
import scalaz.syntax.bifunctor._
import scalaz.concurrent.Task
import scalaz.stream.Process._


class EntityDecoderSpec extends Http4sSpec with PendingUntilFixed {

  def getBody(body: EntityBody): Task[Array[Byte]] =
    body.runLog.map(_.reduce(_ ++ _).toArray)

  def strBody(body: String) = emit(body).map(s => ByteVector(s.getBytes))

  "EntityDecoder" can {
    val req = Response(Ok).withBody("foo")
    "flatMapR with success" in {
      req.flatMap { r =>
        EntityDecoder.text
          .flatMapR(s => DecodeResult.success("bar"))
          .decode(r, strict = false)
          .run
      } must returnValue(\/-("bar"))
    }

    "flatMapR with failure" in {
      req.flatMap { r =>
        EntityDecoder.text
          .flatMapR(s => DecodeResult.failure[String](MalformedMessageBodyFailure("bummer")))
          .decode(r, strict = false)
          .run
      } must returnValue(-\/(MalformedMessageBodyFailure("bummer")))
    }

    val nonMatchingDecoder = EntityDecoder.decodeBy[String](MediaRange.`video/*`) { _ =>
      DecodeResult.failure(MalformedMessageBodyFailure("Nope."))
    }

    val decoder1 = EntityDecoder.decodeBy(MediaType.`application/gnutar`) { msg =>
      DecodeResult.success(1)
    }

    val decoder2 = EntityDecoder.decodeBy(MediaType.`application/excel`) { msg =>
      DecodeResult.success(2)
    }

    val failDecoder = EntityDecoder.decodeBy[Int](MediaType.`application/soap+xml`) { msg =>
      DecodeResult.failure(MalformedMessageBodyFailure("Nope."))
    }

    "Check the validity of a message body" in {
      val decoder = EntityDecoder.decodeBy[String](MediaType.`text/plain`) { msg =>
        DecodeResult.failure(InvalidMessageBodyFailure("Nope."))
      }

      val decoded = decoder.decode(Request(headers = Headers(`Content-Type`(MediaType.`text/plain`))), strict = true).run.unsafePerformSync
      val status = decoded.leftMap(_.toHttpResponse(HttpVersion.`HTTP/1.1`).unsafePerformSync.status).toEither

      status must beLeft(Status.UnprocessableEntity)
    }

    "Not match invalid media type" in {
      nonMatchingDecoder.matchesMediaType(MediaType.`text/plain`) must_== false
    }

    "Match valid media range" in {
      EntityDecoder.text.matchesMediaType(MediaType.`text/plain`) must_== true
    }

    "Match valid media type to a range" in {
      EntityDecoder.text.matchesMediaType(MediaType.`text/css`) must_== true
    }

    "Completely customize the response of a ParsingFailure" in {
      val failure = GenericParsingFailure("sanitized", "details", response =
        (httpVersion: HttpVersion) =>
        Response(Status.BadRequest, httpVersion).withBody(ErrorJson("""{"error":"parse error"}""")))

      val contentType = failure.toHttpResponse(HttpVersion.`HTTP/1.1`).unsafePerformSync.headers.get(`Content-Type`)

      "the content type is application/json" ==> {
        contentType must beSome(`Content-Type`(MediaType.`application/json`))
      }
    }

    "Completely customize the response of a DecodeFailure" in {
      val failure = GenericDecodeFailure("unsupported media type: application/xyz because it's Sunday", response =
        (httpVersion: HttpVersion) =>
          Response(Status.UnsupportedMediaType, httpVersion).withBody("not on a Sunday"))

      val body = failure.toHttpResponse(HttpVersion.`HTTP/1.1`).flatMap(_.body.runLast).unsafePerformSync.flatMap(_.decodeUtf8.right.toOption)

      "the content type is application/json" ==> {
        body must beSome("not on a Sunday")
      }
    }

    "Completely customize the response of a MessageBodyFailure" in {
      // customized decoder, with a custom response
      val decoder = EntityDecoder.decodeBy[String](MediaType.`text/plain`) { msg =>
        DecodeResult.failure {
          val invalid = InvalidMessageBodyFailure("Nope.")
          GenericMessageBodyFailure(invalid.message, invalid.cause, (httpVersion: HttpVersion) =>
            Response(Status.UnprocessableEntity, httpVersion).
              withBody(ErrorJson("""{"error":"unprocessable"}""")))
        }
      }

      val decoded = decoder.decode(Request().replaceAllHeaders(`Content-Type`(MediaType.`text/plain`)), strict = true).run.unsafePerformSync
      val contentType = decoded.leftMap(_.toHttpResponse(HttpVersion.`HTTP/1.1`).unsafePerformSync.headers.get(`Content-Type`)).toEither

      "the content type is application/json instead of text/plain" ==> {
        contentType must beLeft(Some(`Content-Type`(MediaType.`application/json`)))
      }
    }

    "decodeStrict" >> {
      "should produce a MediaTypeMissing if message has no content type" in {
        val req = Request()
        decoder1.decode(req, strict = true).run must returnValue(-\/(MediaTypeMissing(decoder1.consumes)))
      }
      "should produce a MediaTypeMismatch if message has unsupported content type" in {
        val tpe = MediaType.`text/css`
        val req = Request(headers = Headers(`Content-Type`(tpe)))
        decoder1.decode(req, strict = true).run must returnValue(-\/(MediaTypeMismatch(tpe, decoder1.consumes)))
      }
    }

    "composing EntityDecoders with orElse" >> {
      "A message with a MediaType that is not supported by any of the decoders" +
        " will be attempted by the last decoder" in {
        val reqMediaType = MediaType.`application/atom+xml`
        val req = Request(headers = Headers(`Content-Type`(reqMediaType)))
        val expected = \/-(2)
        (decoder1 orElse decoder2).decode(req, strict = false).run must returnValue(expected)
      }
      "A catch all decoder will always attempt to decode a message" in {
        val reqSomeOtherMediaType = Request(headers = Headers(`Content-Type`(MediaType.`text/x-h`)))
        val reqNoMediaType = Request()
        val catchAllDecoder = EntityDecoder.decodeBy(MediaRange.`*/*`) { msg =>
          DecodeResult.success(3)
        }
        (decoder1 orElse catchAllDecoder).decode(reqSomeOtherMediaType, strict = true).run must returnValue(\/-(3))
        (catchAllDecoder orElse decoder1).decode(reqSomeOtherMediaType, strict = true).run must returnValue(\/-(3))
        (catchAllDecoder orElse decoder1).decode(reqNoMediaType, strict = true).run must returnValue(\/-(3))
      }
      "if decode is called with strict, will produce a MediaTypeMissing or MediaTypeMismatch " +
      "with ALL supported media types of the composite decoder" in {
        val reqMediaType = MediaType.`text/x-h`
        val expectedMediaRanges = decoder1.consumes ++ decoder2.consumes ++ failDecoder.consumes
        val reqSomeOtherMediaType = Request(headers = Headers(`Content-Type`(reqMediaType)))
        (decoder1 orElse decoder2 orElse failDecoder).decode(reqSomeOtherMediaType, strict = true).run.unsafePerformSync must_==
          DecodeResult.failure(MediaTypeMismatch(reqMediaType, expectedMediaRanges)).run.unsafePerformSync
        (decoder1 orElse decoder2 orElse failDecoder).decode(Request(), strict = true).run.unsafePerformSync must_==
          DecodeResult.failure(MediaTypeMissing(expectedMediaRanges)).run.unsafePerformSync
      }
    }
  }

  "apply" should {
    val request = Request().withBody("whatever")

    "invoke the function with  the right on a success" in {
      val happyDecoder = EntityDecoder.decodeBy(MediaRange.`*/*`)(_ => DecodeResult.success(Task.now("hooray")))
      Task.async[String] { cb =>
        request.decodeWith(happyDecoder, strict = false) { s => cb(\/-(s)); Task.now(Response()) }.unsafePerformSync
        ()
      } must returnValue("hooray")
    }

    "wrap the ParseFailure in a ParseException on failure" in {
      val grumpyDecoder = EntityDecoder.decodeBy(MediaRange.`*/*`)(_ => DecodeResult.failure[String](Task.now(MalformedMessageBodyFailure("Bah!"))))
      val resp = request.decodeWith(grumpyDecoder, strict = false) { _ => Task.now(Response())}.unsafePerformSync
      resp.status must_== (Status.BadRequest)
    }
  }

  "application/x-www-form-urlencoded" should {

    val server: Request => Task[Response] = { req =>
      req.decode[UrlForm] { form => Response(Ok).withBody(form)(UrlForm.entityEncoder(Charset.`UTF-8`)) }
        .handle{ case NonFatal(t) => Response(Status.BadRequest) }
    }

    "Decode form encoded body" in {
      val urlForm = UrlForm(Map(
        "Formula" -> Seq("a + b == 13%!"),
        "Age"     -> Seq("23"),
        "Name"    -> Seq("Jonathan Doe")
      ))
      val resp = Request().withBody(urlForm)(UrlForm.entityEncoder(Charset.`UTF-8`)).flatMap(server)
      resp must beStatus(Ok).run
      resp.flatMap(UrlForm.entityDecoder.decode(_, strict = true).run) must returnValue(\/-(urlForm))
    }

    // TODO: need to make urlDecode strict
    "handle a parse failure" in {
      server(Request(body = strBody("%C"))) must beStatus(Status.BadRequest).run
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
      os.read(data,0,in.length.asInstanceOf[Int])
      data.foldLeft("")(_ + _)
    }

    def mockServe(req: Request)(route: Request => Task[Response]) = {
      route(req.withBody(emit(binData).map(ByteVector(_))))
    }

    "Write a text file from a byte string" in {
      val tmpFile = File.createTempFile("foo","bar")
      try {
        val response = mockServe(Request()) {
          case req =>
            req.decodeWith(textFile(tmpFile), strict = false) { _ =>
              Response(Ok).withBody("Hello")
            }
        }.unsafePerformSync

        readTextFile(tmpFile) must_== (new String(binData))
        response.status must_== (Status.Ok)
        getBody(response.body) must returnValue("Hello".getBytes)
      }
      finally {
        tmpFile.delete()
        ()
      }
    }

    "Write a binary file from a byte string" in {
      val tmpFile = File.createTempFile("foo", "bar")
      try {
        val response = mockServe(Request()) {
          case req => req.decodeWith(binFile(tmpFile), strict = false) { _ => Response(Ok).withBody("Hello")}
        }.unsafePerformSync

        response must beStatus(Status.Ok)
        getBody(response.body) must returnValue("Hello".getBytes)
        readFile(tmpFile) must_== (binData)
      }
      finally {
        tmpFile.delete()
        ()
      }
    }
  }

  "binary EntityDecoder" should {
    "yield an empty array on a bodyless message" in {
      val msg = Request()
      binary.decode(msg, strict = false).run.unsafePerformSync must be_\/-.like { case ByteVector.empty => ok }
    }

    "concat ByteVectors" in {
      val d1 = Array[Byte](1,2,3); val d2 = Array[Byte](4,5,6)
      val body = emit(d1) ++ emit(d2)
      val msg = Request(body = body.map(ByteVector(_)))

      binary.decode(msg, strict = false).run must returnValue(\/-(ByteVector(1, 2, 3, 4, 5, 6)))
    }

    "Match any media type" in {
      binary.matchesMediaType(MediaType.`text/plain`) must_== true
    }
  }

  "decodeString" should {
    val str = "Oekraïene"
    "Use an charset defined by the Content-Type header" in {
      Response(Ok)
        .withBody(str.getBytes(Charset.`UTF-8`.nioCharset))
        .withContentType(Some(`Content-Type`(MediaType.`text/plain`, Some(Charset.`UTF-8`))))
        .flatMap(EntityDecoder.decodeString(_)(Charset.`US-ASCII`)) must returnValue(str)
    }

    "Use the default if the Content-Type header does not define one" in {
      Response(Ok).withBody(str.getBytes(Charset.`UTF-8`.nioCharset))
        .withContentType(Some(`Content-Type`(MediaType.`text/plain`, None)))
        .flatMap(EntityDecoder.decodeString(_)(Charset.`UTF-8`)) must returnValue(str)
    }
  }

  // we want to return a specific kind of error when there is a MessageFailure
  sealed case class ErrorJson(value: String)
  implicit val errorJsonEntityEncoder: EntityEncoder[ErrorJson] =
    EntityEncoder.simple[ErrorJson](`Content-Type`(MediaType.`application/json`))(json =>
      ByteVector(json.value.getBytes()))

}
