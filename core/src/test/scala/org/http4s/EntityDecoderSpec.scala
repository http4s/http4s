package org.http4s

import scala.language.postfixOps

import org.http4s.headers.`Content-Type`
import Status._

import java.io.{FileInputStream,File,InputStreamReader}

import scala.util.control.NonFatal
import scalaz.{\/-, -\/}
import scalaz.stream.Process._
import scalaz.concurrent.{Promise, Task}
import scodec.bits.ByteVector


class EntityDecoderSpec extends Http4sSpec {

  def getBody(body: EntityBody): Array[Byte] = body.runLog.run.reduce(_ ++ _).toArray

  def strBody(body: String) = emit(body).map(s => ByteVector(s.getBytes))

  "EntityDecoder" can {
    val req = Response(Ok).withBody("foo").run
    "flatMapR with success" in {
      EntityDecoder.text
        .flatMapR(s => DecodeResult.success("bar"))
        .decode(req)
        .run
        .run must beRightDisjunction("bar")
    }

    "flatMapR with failure" in {
      EntityDecoder.text
        .flatMapR(s => DecodeResult.failure[String](ParseFailure("bummer")))
        .decode(req)
        .run
        .run must beLeftDisjunction(ParseFailure("bummer"))
    }
  }

  "apply" should {
    val request = Request().withBody("whatever").run

    "invoke the function with  the right on a success" in {
      val happyDecoder = EntityDecoder.decodeBy(MediaRange.`*/*`)(_ => DecodeResult.success(Task.now("hooray")))
      Task.async[String] { cb =>
        request.decodeWith(happyDecoder) { s => cb(\/-(s)); Task.now(Response()) }.run
      }.run must equal ("hooray")
    }

    "wrap the ParseFailure in a ParseException on failure" in {
      val grumpyDecoder = EntityDecoder.decodeBy(MediaRange.`*/*`)(_ => DecodeResult.failure[String](Task.now(ParseFailure("Bah!"))))
      val resp = request.decodeWith(grumpyDecoder) { _ => Task.now(Response())}.run
      resp.status must equal (Status.BadRequest)
    }
  }

  "application/x-www-form-urlencoded" should {

    val server: Request => Task[Response] = { req =>
      req.decodeWith(formEncoded) { form => Response(Ok).withBody(form("Name").head) }
        .handle{ case NonFatal(t) => Response(BadRequest) }
    }

    "Decode form encoded body" in {
      val body = strBody("Name=Jonathan+Doe&Age=23&Formula=a+%2B+b+%3D%3D+13%25%21")
      val result = Map(("Formula",Seq("a + b == 13%!")),
        ("Age",Seq("23")),
        ("Name",Seq("Jonathan Doe")))

      val resp = server(Request(body = body)).run
      resp.status must_== Ok
      getBody(resp.body) must_== "Jonathan Doe".getBytes
    }

    "handle a parse failure" in {
      val body = strBody("%C")
      val resp = server(Request(body = body)).run
      resp.status must_== Status.BadRequest
    }

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

    "Match any media type" in {
      val req = Response(Ok).withBody("foo").run
      binary.matchesMediaType(req) must_== true
    }

    "Not match invalid media type" in {
      val req = Response(Ok).withBody("foo").run
      EntityDecoder.formEncoded.matchesMediaType(req) must_== false
    }

    "Match valid media range" in {
      val req = Response(Ok).withBody("foo").run
      EntityDecoder.text.matchesMediaType(req) must_== true
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
      (EntityDecoder.formEncoded.matchesMediaType(req) must_== false) and
      (EntityDecoder.formEncoded.matchesMediaType(tpe) must_== false)
    }

  }

  "binary EntityDecoder" should {
    "yield an empty array on a bodyless message" in {
      val msg = Request()
      binary.decode(msg).run.run must beRightDisjunction.like { case ByteVector.empty => ok }
    }

    "concat ByteVectors" in {
      val d1 = Array[Byte](1,2,3); val d2 = Array[Byte](4,5,6)
      val body = emit(d1) ++ emit(d2)
      val msg = Request(body = body.map(ByteVector(_)))

      val result = binary.decode(msg).run.run

      result must_== (\/-(ByteVector(1, 2, 3, 4, 5, 6)))
    }
  }


}
