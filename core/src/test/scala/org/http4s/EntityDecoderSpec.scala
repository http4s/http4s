package org.http4s

import scala.language.postfixOps

import org.http4s.Header.`Content-Type`
import Status.Ok
import EntityDecoder._

import org.specs2.mutable.Specification

import org.xml.sax.SAXParseException

import java.io.{FileInputStream,File,InputStreamReader}

import scala.util.control.NonFatal
import scalaz.-\/
import scalaz.stream.Process._
import scalaz.concurrent.Task
import scodec.bits.ByteVector


class EntityDecoderSpec extends Specification {

  def getBody(body: EntityBody): Array[Byte] = body.runLog.run.reduce(_ ++ _).toArray

  def strBody(body: String) = emit(body).map(s => ByteVector(s.getBytes))

  "xml" should {

    val server: Request => Task[Response] = { req =>
      xml(req).flatMap{ elem => ResponseBuilder(Ok, elem.label) }
    }

    "parse the XML" in {
      val resp = server(Request(body = emit("<html><h1>h1</h1></html>").map(s => ByteVector(s.getBytes)))).run
      resp.status must_==(Ok)
      getBody(resp.body) must_== ("html".getBytes)
    }

    "handle a parse failure" in {
      val body = strBody("This is not XML.")
      val tresp = server(Request(body = body))
      tresp.run must throwA[DecodingException]

      val -\/(err) = tresp.attemptRun
      val asresp = err.asInstanceOf[DecodingException].asResponse(HttpVersion.`HTTP/1.1`)
      asresp.status must_== Status.BadRequest
      asresp.httpVersion must_== HttpVersion.`HTTP/1.1`
    }
  }

  "application/x-www-form-urlencoded" should {

    val server: Request => Task[Response] = { req =>
      formEncoded(req).flatMap{ form => ResponseBuilder(Ok, form("Name").head) }
        .handle{ case NonFatal(t) => ResponseBuilder.basic(Status.BadRequest).run }
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
      val response = mocServe(Request()) {
        case req =>
          textFile(tmpFile)(req).flatMap { _ =>
            ResponseBuilder(Ok, "Hello")
          }
      }.run

      readTextFile(tmpFile) must_== (new String(binData))
      response.status must_== (Status.Ok)
      getBody(response.body) must_== ("Hello".getBytes)
    }

    "Write a binary file from a byte string" in {
      val tmpFile = File.createTempFile("foo","bar")
      val response = mocServe(Request()) {
        case req => binFile(tmpFile)(req).flatMap(_ => ResponseBuilder(Ok, "Hello"))
      }.run

      response.status must_== (Status.Ok)
      getBody(response.body) must_== ("Hello".getBytes)
      readFile(tmpFile) must_== (binData)
    }

    "Match any media type" in {
      val req = ResponseBuilder(Ok, "foo").run
      binary.matchesMediaType(req) must_== true
    }

    "Not match invalid media type" in {
      val req = ResponseBuilder(Ok, "foo").run
      EntityDecoder.xml().matchesMediaType(req) must_== false
    }

    "Match valid media range" in {
      val req = ResponseBuilder(Ok, "foo").run
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
      (EntityDecoder.xml().matchesMediaType(req) must_== false) and
      (EntityDecoder.xml().matchesMediaType(tpe) must_== false)
    }

  }

  "binary EntityDecoder" should {
    "yield an empty array on a bodyless message" in {
      val msg = Request()
      binary(msg).run.length should_== 0
    }

    "concat ByteVectors" in {
      val d1 = Array[Byte](1,2,3); val d2 = Array[Byte](4,5,6)
      val body = emit(d1) ++ emit(d2)
      val msg = Request(body = body.map(ByteVector(_)))

      val result = binary(msg).run

      result.length should_== 6
      result should_== ByteVector(1,2,3,4,5,6)
    }
  }

}

