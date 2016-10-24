package org.http4s
package scalaxml

import fs2.{Stream, Task}
import org.http4s.EntityEncoderSpec.writeToString
import org.http4s.Status.Ok

import scala.xml.Elem

class ScalaXmlSpec extends Http4sSpec {

  def getBody(body: EntityBody): Array[Byte] = body.runLog.unsafeRun.toArray

  def strBody(body: String): EntityBody = Stream(body).through(fs2.text.utf8Encode)

  "xml" should {
    val server: Request => Task[Response] = { req =>
      req.decode { elem: Elem => Response(Ok).withBody(elem.label) }
    }

    "parse the XML" in {
      val resp = server(Request(body = strBody("<html><h1>h1</h1></html>"))).unsafeRun
      resp.status must_==(Ok)
      getBody(resp.body) must_== ("html".getBytes)
    }

    "return 400 on parse error" in {
      val body = strBody("This is not XML.")
      val tresp = server(Request(body = body))
      tresp.unsafeRun.status must_== (Status.BadRequest)
    }
  }

  "htmlEncoder" should {
    "render html" in {
      val html = <html><body>Hello</body></html>
      writeToString(html) must_== "<html><body>Hello</body></html>"
    }
  }
}
