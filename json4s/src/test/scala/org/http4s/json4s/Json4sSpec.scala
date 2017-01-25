package org.http4s
package json4s

import cats.syntax.option._
import org.http4s.headers.`Content-Type`
import org.http4s.jawn.JawnDecodeSupportSpec
import org.json4s.JsonFormat
import org.json4s.DefaultReaders._
import org.json4s.DefaultWriters._
import org.json4s.JValue
import org.json4s.JsonAST.{JInt, JField, JString, JObject}

trait Json4sSpec[J] extends JawnDecodeSupportSpec[JValue] { self: Json4sInstances[J] =>
  import Json4sSpec._

  testJsonDecoder(jsonDecoder)

  "json encoder" should {
    val json: JValue = JObject(JField("test", JString("json4s")))

    "have json content type" in {
      jsonEncoder.headers.get(`Content-Type`) must_== Some(`Content-Type`(MediaType.`application/json`))
    }

    "write compact JSON" in {
      writeToString(json) must_== ("""{"test":"json4s"}""")
    }
  }

  "jsonEncoderOf" should {
    "have json content type" in {
      jsonEncoderOf[Option[Int]].headers.get(`Content-Type`) must_== Some(`Content-Type`(MediaType.`application/json`))
    }

    "write compact JSON with a json4s writer" in {
      writeToString(42.some)(jsonEncoderOf[Option[Int]]) must_== ("""42""")
    }
  }

  "jsonOf" should {
    "decode JSON from an json4s reader" in {
      val result = jsonOf[Int].decode(Request().withBody("42").unsafeRun, strict = false)
      result.value.unsafeRun must beRight(42)
    }

    "handle reader failures" in {
      val result = jsonOf[Int].decode(Request().withBody(""""oops"""").unsafeRun, strict = false)
      result.value.unsafeRun must beLeft.like {
        case InvalidMessageBodyFailure("Could not map JSON", _) => ok
      }
    }
  }

  "jsonExtract" should {
    implicit val formats = org.json4s.DefaultFormats

    "extract JSON from formats" in {
      val result = jsonExtract[Foo].decode(Request().withBody(JObject("bar" -> JInt(42))).unsafeRun, strict = false)
      result.value.unsafeRun must beRight(Foo(42))
    }

    "handle extract failures" in {
      val result = jsonExtract[Foo].decode(Request().withBody(""""oops"""").unsafeRun, strict = false)
      result.value.unsafeRun must beLeft.like {
        case InvalidMessageBodyFailure("Could not extract JSON", _) => ok
      }
    }
  }

  "JsonFormat[Uri]" should {
    "round trip" in {
      // TODO would benefit from Arbitrary[Uri]
      val uri = Uri.uri("http://www.example.com/")
      val format = implicitly[JsonFormat[Uri]]
      format.read(format.write(uri)) must_== (uri)
    }
  }
}

object Json4sSpec {
  final case class Foo(bar: Int)
}
