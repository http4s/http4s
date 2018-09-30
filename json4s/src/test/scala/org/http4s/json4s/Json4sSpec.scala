package org.http4s
package json4s

import cats.effect.IO
import cats.implicits._
import org.http4s.headers.`Content-Type`
import org.http4s.jawn.JawnDecodeSupportSpec
import org.json4s.{JValue, JsonFormat}
import org.json4s.DefaultReaders._
import org.json4s.DefaultWriters._
import org.json4s.JsonAST.{JField, JInt, JObject, JString}

trait Json4sSpec[J] extends JawnDecodeSupportSpec[JValue] { self: Json4sInstances[J] =>
  import Json4sSpec._

  testJsonDecoder(jsonDecoder)

  "json encoder" should {
    val json: JValue = JObject(JField("test", JString("json4s")))

    "have json content type" in {
      jsonEncoder[IO, JValue].headers.get(`Content-Type`) must beSome(
        `Content-Type`(MediaType.application.json))
    }

    "write compact JSON" in {
      writeToString(json) must_== """{"test":"json4s"}"""
    }
  }

  "jsonEncoderOf" should {
    "have json content type" in {
      jsonEncoderOf[IO, Option[Int]].headers.get(`Content-Type`) must beSome(
        `Content-Type`(MediaType.application.json))
    }

    "write compact JSON with a json4s writer" in {
      writeToString(42.some)(jsonEncoderOf[IO, Option[Int]]) must_== """42"""
    }
  }

  "jsonOf" should {
    "decode JSON from an json4s reader" in {
      val result =
        jsonOf[IO, Int].decode(Request[IO]().withEntity("42"), strict = false)
      result.value.unsafeRunSync must beRight(42)
    }

    "handle reader failures" in {
      val result =
        jsonOf[IO, Int].decode(Request[IO]().withEntity(""""oops""""), strict = false)
      result.value.unsafeRunSync must beLeft.like {
        case InvalidMessageBodyFailure("Could not map JSON", _) => ok
      }
    }
  }

  "jsonExtract" should {
    implicit val formats = org.json4s.DefaultFormats

    "extract JSON from formats" in {
      val result = jsonExtract[IO, Foo]
        .decode(Request[IO]().withEntity(JObject("bar" -> JInt(42))), strict = false)
      result.value.unsafeRunSync must beRight(Foo(42))
    }

    "handle extract failures" in {
      val result = jsonExtract[IO, Foo]
        .decode(Request[IO]().withEntity(""""oops""""), strict = false)
      result.value.unsafeRunSync must beLeft.like {
        case InvalidMessageBodyFailure("Could not extract JSON", _) => ok
      }
    }
  }

  "JsonFormat[Uri]" should {
    "round trip" in {
      // TODO would benefit from Arbitrary[Uri]
      val uri = Uri.uri("http://www.example.com/")
      val format = implicitly[JsonFormat[Uri]]
      format.read(format.write(uri)) must_== uri
    }
  }

  "Message[F].decodeJson[A]" should {
    "decode json from a message" in {
      val req = Request[IO]()
        .withEntity("42")
        .withContentType(`Content-Type`(MediaType.application.json))
      req.decodeJson[Option[Int]] must returnValue(Some(42))
    }

    "fail on invalid json" in {
      val req = Request[IO]()
        .withEntity("not a number")
        .withContentType(`Content-Type`(MediaType.application.json))
      req.decodeJson[Int].attempt.unsafeRunSync must beLeft
    }
  }
}

object Json4sSpec {
  final case class Foo(bar: Int)
}
