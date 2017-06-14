package org.http4s
package scalaxml

import cats.effect.IO
import fs2.Stream
import fs2.text.utf8Encode
import org.http4s.Status.Ok

import scala.xml.Elem

class ScalaXmlSpec extends Http4sSpec {

  def getBody(body: EntityBody[IO]): Array[Byte] = body.runLog.unsafeRunSync.toArray

  def strBody(body: String): EntityBody[IO] = Stream(body).through(utf8Encode)

  "xml" should {
    val server: Request[IO] => IO[Response[IO]] = { req =>
      req.decode { elem: Elem => Response[IO](Ok).withBody(elem.label) }
    }

    "parse the XML" in {
      val resp = server(Request[IO](body = strBody("<html><h1>h1</h1></html>"))).unsafeRunSync
      resp.status must_==(Ok)
      getBody(resp.body) must_== ("html".getBytes)
    }

    "return 400 on parse error" in {
      val body = strBody("This is not XML.")
      val tresp = server(Request[IO](body = body))
      tresp.unsafeRunSync.status must_== (Status.BadRequest)
    }
  }

  "htmlEncoder" should {
    "render html" in {
      val html = <html><body>Hello</body></html>
      writeToString(html) must_== "<html><body>Hello</body></html>"
    }
  }
}
