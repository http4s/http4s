package org.http4s
package scalatags

import cats.effect.IO
import org.http4s.Status.Ok
import _root_.scalatags.Text
import cats.data.NonEmptyList
import org.http4s.headers.`Content-Type`

class ScalatagsSpec extends Http4sSpec {

  private val testCharsets = NonEmptyList.of(
    Charset.`ISO-8859-1`,
    Charset.fromString("Windows-1251").yolo,
    Charset.fromString("GB2312").yolo,
    Charset.fromString("Shift-JIS").yolo,
    Charset.fromString("Windows-1252").yolo
  )

  private def testBody() = {
    import Text.all
    all.div()(
      all.p()(all.raw("this is my testBody"))
    )
  }

  "TypedTag encoder" should {

    "return Content-Type text/html with proper charset" in {
      testCharsets.forall { implicit cs =>
        val headers = EntityEncoder[IO, Text.TypedTag[String]].headers
        headers.get(`Content-Type`) must_== Some(`Content-Type`(MediaType.text.html, Some(cs)))
      }
    }

    "render the body" in {
      val resp = Response[IO](Ok).withEntity(testBody())
      EntityDecoder.text[IO].decode(resp, strict = false).value.unsafeRunSync must beRight(
        "<div><p>this is my testBody</p></div>")
    }
  }
}
