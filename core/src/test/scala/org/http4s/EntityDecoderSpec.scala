package org.http4s

import org.http4s.Status.Ok
import org.specs2.execute.{PendingUntilFixed, Pending}
import scodec.bits.ByteVector
import org.http4s.headers.`Content-Type`

import java.io.{FileInputStream,File,InputStreamReader}

import scala.language.postfixOps
import scala.util.control.NonFatal
import scalaz.\/-
import scalaz.concurrent.Task
import scalaz.stream.Process._


class EntityDecoderSpec extends Http4sSpec with PendingUntilFixed {

  def getBody(body: EntityBody): Array[Byte] = body.runLog.run.reduce(_ ++ _).toArray

  def strBody(body: String) = emit(body).map(s => ByteVector(s.getBytes))

  "EntityDecoder" can {
    val req = Response(Ok).withBody("foo").run
    "flatMapR with success" in {
      EntityDecoder.text
        .flatMapR(s => DecodeResult.success("bar"))
        .decode(req)
        .run
        .run must be_\/-("bar")
    }

    "flatMapR with failure" in {
      EntityDecoder.text
        .flatMapR(s => DecodeResult.failure[String](ParseFailure("bummer", "real bummer")))
        .decode(req)
        .run
        .run must be_-\/(ParseFailure("bummer", "real bummer"))
    }

    val nonMatchingDecoder = EntityDecoder.decodeBy[String](MediaRange.`video/*`) { _ =>
      DecodeResult.failure(ParseFailure("Nope.", ""))
    }

    val strictDecoder = EntityDecoder.decodeBy(MediaType.`application/gnutar`) { msg =>
      DecodeResult.success(1)
    }

    val otherStrictDecoder = EntityDecoder.decodeBy(MediaType.`application/excel`) { msg =>
      DecodeResult.success(2)
    }

    "Not match invalid media type" in {
      val req = Response(Ok).withBody("foo").run
      nonMatchingDecoder.matchesMediaType(req) must_== false
    }

    "Match valid media range" in {
      val req = Response(Ok).withBody("foo").run
      EntityDecoder.text.matchesMediaType(req) must_== true
    }

    "Match request without media type only if isLenient is true" in {
      val req = Request()
      strictDecoder.matchesMediaType(req) must_== false
      strictDecoder.asLenient.matchesMediaType(req) must_== true
    }

    "Match valid media type to a range" in {
      val req = Request(headers = Headers(`Content-Type`(MediaType.`text/css`)))
      EntityDecoder.text.matchesMediaType(req) must_== true
    }

    "Match with consistent behavior" in {
      val tpe = MediaType.`text/css`
      val req = Request(headers = Headers(`Content-Type`(tpe)))
      (EntityDecoder.text.matchesMediaType(req) must_== true)   and
        (EntityDecoder.text.matchesMediaType(tpe) must_== true)   and
        (nonMatchingDecoder.matchesMediaType(req) must_== false) and
        (nonMatchingDecoder.matchesMediaType(tpe) must_== false)
    }

    "composing EntityDecoders with orElse" >> {
      "a composite decoder is lenenient if any of it's child decoders are lenient" in {
        (strictDecoder orElse otherStrictDecoder).isLenient must_== false
        (strictDecoder.asLenient orElse otherStrictDecoder).isLenient must_== true
        (strictDecoder orElse otherStrictDecoder.asLenient).isLenient must_== true
        (strictDecoder.asLenient orElse otherStrictDecoder.asLenient).isLenient must_== true
      }
      "if the first decoder is lenient, it will attempt to decode a Message without a MediaType" in {
        val req = Request()
        (strictDecoder.asLenient orElse otherStrictDecoder).decode(req) must_== DecodeResult.success(1)
      }
      "if both decoders are strict, a message without a MediaType will produce a MediaTypeMissing decode failure" in {
        val req = Request()
        val consumes = strictDecoder.consumes ++ otherStrictDecoder.consumes
        (strictDecoder orElse otherStrictDecoder).decode(req) must_== DecodeResult.failure(MediaTypeMissing(consumes))
      }
      "A message with a MediaType that is not supported by any of the decoders" +
        " will always produce a MediaTypeMismatch decode failure" in {
        val reqMediaType = MediaType.`application/atom+xml`
        val req = Request(headers = Headers(`Content-Type`(reqMediaType)))
        val expected = DecodeResult.failure(MediaTypeMismatch(reqMediaType, strictDecoder.consumes ++ otherStrictDecoder.consumes))
        (strictDecoder orElse otherStrictDecoder).decode(req) must_== expected
        (strictDecoder.asLenient orElse otherStrictDecoder).decode(req) must_== expected
        (strictDecoder orElse otherStrictDecoder.asLenient).decode(req) must_== expected
        (strictDecoder.asLenient orElse otherStrictDecoder.asLenient).decode(req) must_== expected
      }
      "A catch all decoder will always attempt to decode a message" in {
        val reqSomeOtherMediaType = Request(headers = Headers(`Content-Type`(MediaType.`text/x-h`)))
        val reqNoMediaType = Request()
        val catchAllDecoder = EntityDecoder.decodeBy(MediaRange.`*/*`) { msg =>
          DecodeResult.success(3)
        }
        (strictDecoder orElse catchAllDecoder).decode(reqSomeOtherMediaType) must_== DecodeResult.success(3)
        (strictDecoder orElse catchAllDecoder).decode(reqNoMediaType) must_== DecodeResult.success(3)
      }
    }
  }

  "apply" should {
    val request = Request().withBody("whatever").run

    "invoke the function with  the right on a success" in {
      val happyDecoder = EntityDecoder.decodeBy(MediaRange.`*/*`)(_ => DecodeResult.success(Task.now("hooray")))
      Task.async[String] { cb =>
        request.decodeWith(happyDecoder) { s => cb(\/-(s)); Task.now(Response()) }.run
      }.run must_== ("hooray")
    }

    "wrap the ParseFailure in a ParseException on failure" in {
      val grumpyDecoder = EntityDecoder.decodeBy(MediaRange.`*/*`)(_ => DecodeResult.failure[String](Task.now(ParseFailure("Bah!", ""))))
      val resp = request.decodeWith(grumpyDecoder) { _ => Task.now(Response())}.run
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
      val resp = Request().withBody(urlForm)(UrlForm.entityEncoder(Charset.`UTF-8`)).flatMap(server).run
      resp.status must_== Ok
      UrlForm.entityDecoder.decode(resp).run.run must_== \/-(urlForm)
    }

    // TODO: need to make urlDecode strict
    "handle a parse failure" in {
      val resp = server(Request(body = strBody("%C"))).run
      resp.status must_== Status.BadRequest
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

    def mocServe(req: Request)(route: Request => Task[Response]) = {
      route(req.copy(body = emit(binData).map(ByteVector(_))))
    }

    "Write a text file from a byte string" in {
      val tmpFile = File.createTempFile("foo","bar")
      try {
        val response = mocServe(Request()) {
          case req =>
            req.decodeWith(textFile(tmpFile)) { _ =>
              Response(Ok).withBody("Hello")
            }
        }.run

        readTextFile(tmpFile) must_== (new String(binData))
        response.status must_== (Status.Ok)
        getBody(response.body) must_== ("Hello".getBytes)
      }
      finally {
        tmpFile.delete()
      }
    }

    "Write a binary file from a byte string" in {
      val tmpFile = File.createTempFile("foo", "bar")
      try {
        val response = mocServe(Request()) {
          case req => req.decodeWith(binFile(tmpFile)) { _ => Response(Ok).withBody("Hello")}
        }.run

        response.status must_== (Status.Ok)
        getBody(response.body) must_== ("Hello".getBytes)
        readFile(tmpFile) must_== (binData)
      }
      finally {
        tmpFile.delete()
      }
    }
  }

  "binary EntityDecoder" should {
    "yield an empty array on a bodyless message" in {
      val msg = Request()
      binary.decode(msg).run.run must be_\/-.like { case ByteVector.empty => ok }
    }

    "concat ByteVectors" in {
      val d1 = Array[Byte](1,2,3); val d2 = Array[Byte](4,5,6)
      val body = emit(d1) ++ emit(d2)
      val msg = Request(body = body.map(ByteVector(_)))

      val result = binary.decode(msg).run.run

      result must_== (\/-(ByteVector(1, 2, 3, 4, 5, 6)))
    }

    "Match any media type" in {
      val req = Response(Ok).withBody("foo").run
      binary.matchesMediaType(req) must_== true
    }
  }

  "decodeString" should {
    val str = "Oekraïene"
    "Use an charset defined by the Content-Type header" in {
      val msg = Response(Ok).withBody(str.getBytes(Charset.`UTF-8`.nioCharset))
                            .withContentType(Some(`Content-Type`(MediaType.`text/plain`, Some(Charset.`UTF-8`))))
                            .run

      EntityDecoder.decodeString(msg)(Charset.`US-ASCII`).run must_== str
    }

    "Use the default if the Content-Type header does not define one" in {
      val msg = Response(Ok).withBody(str.getBytes(Charset.`UTF-8`.nioCharset))
                            .withContentType(Some(`Content-Type`(MediaType.`text/plain`, None)))
                            .run

      EntityDecoder.decodeString(msg)(Charset.`UTF-8`).run must_== str
    }
  }
}
