package org.http4s
package scalaxml

import scodec.bits.ByteVector

import scala.xml.Elem
import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz.stream.Process.emit
import scalaz.stream.text.utf8Decode
import Status.Ok

class ScalaXmlSpec extends Http4sSpec {

  def getBody(body: EntityBody): Array[Byte] = body.runLog.run.reduce(_ ++ _).toArray

  def strBody(body: String) = emit(body).map(s => ByteVector(s.getBytes))

  implicit val byteVectorMonoid: scalaz.Monoid[ByteVector] = scalaz.Monoid.instance(_ ++ _, ByteVector.empty)

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
  }

  "htmlEncoder" should {
    "render html" in {
      val html = <html><body>Hello</body></html>
      writeToString(html) must_== "<html><body>Hello</body></html>"
    }
  }
}
