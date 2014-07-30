package org.http4s

import org.http4s.Header.`Content-Type`
import org.specs2.mutable.Specification

import scala.language.postfixOps
import org.xml.sax.SAXParseException
import scodec.bits.ByteVector

import scalaz.concurrent.Task

// the http4s team resents importing this.
import org.http4s.Http4s._

import java.io.{FileInputStream,File,InputStreamReader}

import scalaz.stream.Process._
import EntityDecoder._


class EntityDecoderSpec extends Specification {

  def getBody(body: EntityBody): Array[Byte] = body.runLog.run.reduce(_ ++ _).toArray

  "xml" should {

    val server: Request => Task[Response] = { req =>
      xml(req).flatMap{ elem => Ok(elem.label) }
                    .handle{ case t: SAXParseException => Status.BadRequest().run }
    }

    "parse the XML" in {
      val resp = server(Request(body = emit("<html><h1>h1</h1></html>").map(s => ByteVector(s.getBytes)))).run
      resp.status must_==(Status.Ok)
      getBody(resp.body) must_== ("html".getBytes)
    }

    "handle a parse failure" in {
      val body = emit("This is not XML.").map(s => ByteVector(s.getBytes))
      val resp = server(Request(body = body)).run
      resp.status must_== (Status.BadRequest)
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
          textFile(tmpFile).decode(req).flatMap { _ =>
            Ok("Hello")
          }
      }.run

      readTextFile(tmpFile) must_== (new String(binData))
      response.status must_== (Status.Ok)
      getBody(response.body) must_== ("Hello".getBytes)
    }

    "Write a binary file from a byte string" in {
      val tmpFile = File.createTempFile("foo","bar")
      val response = mocServe(Request()) {
        case req => binFile(tmpFile).decode(req).flatMap(_ => Ok("Hello"))
      }.run

      response.status must_== (Status.Ok)
      getBody(response.body) must_== ("Hello".getBytes)
      readFile(tmpFile) must_== (binData)
    }

    "Match any media type" in {
      val req = Ok("foo").run
      binary.matchesMediaType(req) must_== true
    }

    "Not match invalid media type" in {
      val req = Ok("foo").run
      xml().matchesMediaType(req) must_== false
    }

  }

}

