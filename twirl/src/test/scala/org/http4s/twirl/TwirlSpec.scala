package org.http4s
package twirl

import cats.effect.IO
import org.http4s.Status.Ok
import org.http4s.headers.`Content-Type`
import org.scalacheck.{Arbitrary, Gen}
import play.twirl.api.{Html, JavaScript, Txt, Xml}

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
    "return Content-Type text/html with proper charset" in prop { implicit cs: Charset =>
      val headers = EntityEncoder[IO, Html].headers
      headers.get(`Content-Type`) must_== Some(`Content-Type`(MediaType.`text/html`, Some(cs)))
    }

    "render the body" in prop { implicit cs: Charset =>
      val resp = Response[IO](Ok).withBody(html.test())
      text[IO].decode(resp.unsafeRunSync, strict = false).value.unsafeRunSync must beRight("<h1>test html</h1>")
    }
  }

  "JS encoder" should {
    "return Content-Type application/javascript with proper charset" in prop { implicit cs: Charset =>
      val headers = EntityEncoder[IO, JavaScript].headers
      headers.get(`Content-Type`) must_== Some(`Content-Type`(MediaType.`application/javascript`, Some(cs)))
    }

    "render the body" in prop { implicit cs: Charset =>
      val resp = Response[IO](Ok).withBody(js.test())
      text[IO].decode(resp.unsafeRunSync, strict = false).value.unsafeRunSync must beRight(""""test js"""")
    }
  }

  "Text encoder" should {
    "return Content-Type text/plain with proper charset" in prop { implicit cs: Charset =>
      val headers = EntityEncoder[IO, Txt].headers
      headers.get(`Content-Type`) must_== Some(`Content-Type`(MediaType.`text/plain`, Some(cs)))
    }

    "render the body" in prop { implicit cs: Charset =>
      val resp = Response[IO](Ok).withBody(txt.test())
      text[IO].decode(resp.unsafeRunSync, strict = false).value.unsafeRunSync must beRight("""test text""")
    }
  }

  "XML encoder" should {
    "return Content-Type application/xml with proper charset" in prop { implicit cs: Charset =>
      val headers = EntityEncoder[IO, Xml].headers
      headers.get(`Content-Type`) must_== Some(`Content-Type`(MediaType.`application/xml`, Some(cs)))
    }

    "render the body" in prop { implicit cs: Charset =>
      val resp = Response[IO](Ok).withBody(_root_.xml.test())
      text[IO].decode(resp.unsafeRunSync, strict = false).value.unsafeRunSync must beRight("<test>test xml</test>")
    }
  }
}
