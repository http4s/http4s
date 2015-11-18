package org.http4s
package twirl

import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import play.twirl.api.{JavaScript, Html, Txt, Xml}
import headers.`Content-Type`
import Status.Ok

class TwirlSpec extends Http4sSpec {
  implicit val arbCharset: Arbitrary[Charset] = Arbitrary {
    Gen.oneOf(
      Charset.`UTF-8`,
      Charset.`ISO-8859-1`,
      Charset.fromString("Windows-1251").yolo,
      Charset.fromString("GB2312").yolo,
      Charset.fromString("Shift-JIS").yolo,
      Charset.fromString("Windows-1252").yolo
    )
  }

  "HTML encoder" should {
    "return Content-Type text/htmlwith proper charset" in prop { implicit cs: Charset =>
      val headers = EntityEncoder[Html].headers
      headers.get(`Content-Type`) must_== Some(`Content-Type`(MediaType.`text/html`, Some(cs)))
    }

    "render the body" in prop { implicit cs: Charset =>
      val resp = Response(Ok).withBody(html.test())
      text.decode(resp.run, strict = false).run.run must be_\/-("<h1>test html</h1>")
    }
  }

  "JS encoder" should {
    "return Content-Type application/javascript with proper charset" in prop { implicit cs: Charset =>
      val headers = EntityEncoder[JavaScript].headers
      headers.get(`Content-Type`) must_== Some(`Content-Type`(MediaType.`application/javascript`, Some(cs)))
    }

    "render the body" in prop { implicit cs: Charset =>
      val resp = Response(Ok).withBody(js.test())
      text.decode(resp.run, strict = false).run.run must be_\/-(""""test js"""")
    }
  }

  "Text encoder" should {
    "return Content-Type text/plain with proper charset" in prop { implicit cs: Charset =>
      val headers = EntityEncoder[Txt].headers
      headers.get(`Content-Type`) must_== Some(`Content-Type`(MediaType.`text/plain`, Some(cs)))
    }

    "render the body" in prop { implicit cs: Charset =>
      val resp = Response(Ok).withBody(txt.test())
      text.decode(resp.run, strict = false).run.run must be_\/-("""test text""")
    }
  }

  "XML encoder" should {
    "return Content-Type application/xml with proper charset" in prop { implicit cs: Charset =>
      val headers = EntityEncoder[Xml].headers
      headers.get(`Content-Type`) must_== Some(`Content-Type`(MediaType.`application/xml`, Some(cs)))
    }

    "render the body" in prop { implicit cs: Charset =>
      val resp = Response(Ok).withBody(_root_.xml.test())
      text.decode(resp.run, strict = false).run.run must be_\/-("<test>test xml</test>")
    }
  }
}
