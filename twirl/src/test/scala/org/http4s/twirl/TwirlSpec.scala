package org.http4s
package twirl

import cats.effect.IO
import org.http4s.Status.Ok
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
      val e = EntityEncoder[IO, Html].toEntity(html.test())
      e.mediaType must beSome(MediaType.text.html)
      e.charset must beSome(cs)
    }

    "render the body" in prop { implicit cs: Charset =>
      val resp = Response[IO](Ok).withEntity(html.test())
      EntityDecoder.text[IO].decode(resp, strict = false).value.unsafeRunSync must beRight(
        "<h1>test html</h1>")
    }
  }

  "JS encoder" should {
    "return Content-Type application/javascript with proper charset" in prop {
      implicit cs: Charset =>
        val e = EntityEncoder[IO, JavaScript].toEntity(js.test())
        e.mediaType must beSome(MediaType.application.javascript)
        e.charset must beSome(cs)
    }

    "render the body" in prop { implicit cs: Charset =>
      val resp = Response[IO](Ok).withEntity(js.test())
      EntityDecoder.text[IO].decode(resp, strict = false).value.unsafeRunSync must beRight(
        """"test js"""")
    }
  }

  "Text encoder" should {
    "return Content-Type text/plain with proper charset" in prop { implicit cs: Charset =>
      val e = EntityEncoder[IO, Txt].toEntity(txt.test())
      e.mediaType must beSome(MediaType.text.plain)
      e.charset must beSome(cs)
    }

    "render the body" in prop { implicit cs: Charset =>
      val resp = Response[IO](Ok).withEntity(txt.test())
      EntityDecoder.text[IO].decode(resp, strict = false).value.unsafeRunSync must beRight(
        """test text""")
    }
  }

  "XML encoder" should {
    "return Content-Type application/xml with proper charset" in prop { implicit cs: Charset =>
      val e = EntityEncoder[IO, Xml].toEntity(_root_.xml.test())
      e.mediaType must beSome(MediaType.application.xml)
      e.charset must beSome(cs)
    }

    "render the body" in prop { implicit cs: Charset =>
      val resp = Response[IO](Ok).withEntity(_root_.xml.test())
      EntityDecoder.text[IO].decode(resp, strict = false).value.unsafeRunSync must beRight(
        "<test>test xml</test>")
    }
  }
}
