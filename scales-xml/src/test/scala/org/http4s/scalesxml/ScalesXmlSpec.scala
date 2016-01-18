package org.http4s
package scalesxml

import org.http4s.Status.Ok

import scodec.bits.ByteVector

import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz.stream.Process.emit
import scalaz.stream.text.utf8Decode

import scales.xml.{asString, Elem, Declaration, Doc, Prolog, Xml11}
import scales.xml.ScalesXml._


class ScalesXmlSpec extends Http4sSpec {

  implicit val xmlVersion = Xml11

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

  "xml decoder" should {
    val server: Request => Task[Response] = { req =>
      req.decode { xml: Doc => Response(Ok).withBody(xml.rootElem.section.name.qName) }
    }

    "parse the XML" in {
      val resp = server(Request(body = emit( asString(scales.xml.<(Elem("html")) / Elem("h1") ~> "h1") )
        .map(s => ByteVector(s.getBytes)))).run
      resp.status must_==(Ok)
      getBody(resp.body) must_== ("html".getBytes)
    }

    "return 400 on parse error" in {
      val body = strBody("This is not XML.")
      val response = server(Request(body = body))
      response.run.status must_== (Status.BadRequest)
    }
  }

  "html encoder" should {
    "render html" in {
      val tree = scales.xml.<(Elem("html")) / (Elem("body") ~> "Hello")
      val html: Doc = Doc(tree, Prolog(Declaration(xmlVersion)))
      writeToString(html) must_== "<?xml version=\"1.1\" encoding=\"UTF-8\"?><html><body>Hello</body></html>"
    }
  }
}
