package org.http4s
package scalaxml

import scodec.bits.ByteVector

import scala.xml.Elem
import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz.stream.Process.emit
import scalaz.stream.text.utf8Decode
import org.http4s.Status.Ok
import org.http4s.util.byteVector._
import org.specs2.matcher.Matcher

class ScalaXmlSpec extends Http4sSpec with XMLGenerators {

  def getBody(body: EntityBody): Array[Byte] = body.runLog.run.reduce(_ ++ _).toArray

  def strBody(body: String) = emit(body).map(s => ByteVector(s.getBytes))

  def writeToString[A](a: A)(implicit W: EntityEncoder[A]): String =
    Process.eval(W.toEntity(a))
      .collect { case EntityEncoder.Entity(body, _ ) => body }
      .flatMap(identity)
      .fold1Monoid
      .pipe(utf8Decode)
      .runLastOr("")
      .run

  "xml" should {
    val server: Request => Task[Response] = { req =>
      req.decode { elem: Elem => Response(Ok).withBody(elem.label) }
    }

    "parse the XML" in {
      val resp = server(Request(body = emit("<html><h1>h1</h1></html>").map(s => ByteVector(s.getBytes)))).run
      resp.status must_==(Ok)
      getBody(resp.body) must_== ("html".getBytes)
    }

    "return 400 on parse error" in {
      val body = strBody("This is not XML.")
      val tresp = server(Request(body = body))
      tresp.run.status must_== (Status.BadRequest)
    }

    "round trip" in prop { elem: Elem =>
      EntityEncoder[Elem].toEntity(elem).flatMap { case EntityEncoder.Entity(body, _) =>
        Response().withBody(body).flatMap { resp =>
          resp.as[Elem]
        }
      }.run must xml_==(elem)
    }
  }

  "htmlEncoder" should {
    "render html" in {
      val html = <html><body>Hello</body></html>
      writeToString(html) must_== "<html><body>Hello</body></html>"
    }
  }

  def xml_==(e2: Elem): Matcher[Elem] = { e: Elem =>
    (e xml_== e2, s"$e was not XML-equal to $e2}")
  }
}
