package org.http4s
package play.test // Get out of play package so we can import custom instances

import _root_.play.api.libs.json._
import cats.effect.IO
import cats.effect.laws.util.TestContext
import org.http4s.headers.`Content-Type`
import org.http4s.jawn.JawnDecodeSupportSpec
import org.http4s.play._

// Originally based on CirceSpec
class PlaySpec extends JawnDecodeSupportSpec[JsValue] {
  implicit val testContext = TestContext()

  testJsonDecoder(jsonDecoder)

  sealed case class Foo(bar: Int)
  val foo = Foo(42)
  implicit val format: OFormat[Foo] = Json.format[Foo]

  "json encoder" should {
    val json: JsValue = Json.obj("test" -> JsString("PlaySupport"))

    "have json content type" in {
      jsonEncoder.headers.get(`Content-Type`) must_== Some(
        `Content-Type`(MediaType.application.json))
    }

    "write JSON" in {
      writeToString(json) must_== ("""{"test":"PlaySupport"}""")
    }

  }

  "jsonEncoderOf" should {
    "have json content type" in {
      jsonEncoderOf[IO, Foo].headers.get(`Content-Type`) must_== Some(
        `Content-Type`(MediaType.application.json))
    }

    "write compact JSON" in {
      writeToString(foo)(jsonEncoderOf[IO, Foo]) must_== ("""{"bar":42}""")
    }

  }

  "jsonOf" should {
    "decode JSON from a Play decoder" in {
      val result = jsonOf[IO, Foo]
        .decode(Request[IO]().withEntity(Json.obj("bar" -> JsNumber(42)): JsValue), strict = true)
      result.value.unsafeRunSync must_== Right(Foo(42))
    }
  }

  "Uri codec" should {
    "round trip" in {
      // TODO would benefit from Arbitrary[Uri]
      val uri = Uri.uri("http://www.example.com/")

      Json.fromJson[Uri](Json.toJson(uri)).asOpt must_== (Some(uri))
    }
  }

  "Message[F].decodeJson[A]" should {
    "decode json from a message" in {
      val req = Request[IO]().withEntity(Json.toJson(foo))
      req.decodeJson[Foo] must returnValue(foo)
    }

    "fail on invalid json" in {
      val req = Request[IO]().withEntity(Json.toJson(List(13, 14)))
      req.decodeJson[Foo].attempt.unsafeRunSync must beLeft
    }
  }

  "PlayEntityCodec" should {
    "decode json without defining EntityDecoder" in {
      import org.http4s.play.PlayEntityDecoder._
      val request = Request[IO]().withEntity(Json.obj("bar" -> JsNumber(42)): JsValue)
      val result = request.as[Foo]
      result.unsafeRunSync must_== Foo(42)
    }

    "encode without defining EntityEncoder using default printer" in {
      import org.http4s.play.PlayEntityEncoder._
      writeToString(foo) must_== """{"bar":42}"""
    }
  }
}
