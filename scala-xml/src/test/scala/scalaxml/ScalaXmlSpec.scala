package org.http4s
package scalaxml

import scodec.bits.ByteVector

import scala.xml.Elem
import java.util.concurrent.Executors
import scalaz.concurrent.{Strategy, Task}
import scalaz.stream.Process
import scalaz.stream.Process.emit
import scalaz.stream.text.utf8Decode
import Status.Ok

class ScalaXmlSpec extends Http4sSpec {

  def getBody(body: EntityBody): Array[Byte] = body.runLog.unsafePerformSync.reduce(_ ++ _).toArray

  def strBody(body: String) = emit(body).map(s => ByteVector(s.getBytes))

  implicit val byteVectorMonoid: scalaz.Monoid[ByteVector] = scalaz.Monoid.instance(_ ++ _, ByteVector.empty)

  "xml" should {
    val server: Request => Task[Response] = { req =>
      req.decode[Elem] { elem =>
        Response(Ok).withBody(elem.label) }
    }

    "parse the XML" in {
      val resp = server(Request(body = emit("<html><h1>h1</h1></html>").map(s => ByteVector(s.getBytes)))).unsafePerformSync
      resp.status must_==(Ok)
      getBody(resp.body) must_== ("html".getBytes)
    }

    "parse XML in parallel" in {
      // https://github.com/http4s/http4s/issues/1209
      val pool = Executors.newFixedThreadPool(5)
      val resp = Task.gatherUnordered((0 to 5).map(_ => Task.fork(server(Request(body = strBody("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><html><h1>h1</h1></html>"""))))(pool))).unsafePerformSync
      resp.forall(_.status must_==(Ok))
      resp.forall(x => getBody(x.body) must_== ("html".getBytes))
    }

    "return 400 on parse error" in {
      val body = strBody("This is not XML.")
      val tresp = server(Request(body = body))
      tresp.unsafePerformSync.status must_== (Status.BadRequest)
    }
  }

  "htmlEncoder" should {
    "render html" in {
      val html = <html><body>Hello</body></html>
      writeToString(html) must_== "<html><body>Hello</body></html>"
    }
  }
}
