package org.http4s
package json4s

import org.http4s.EntityEncoderSpec._
import org.http4s.headers.`Content-Type`
import org.http4s.jawn.JawnDecodeSupportSpec
import org.json4s.DefaultReaders._
import org.json4s.DefaultWriters._
import org.json4s.JValue
import org.json4s.JsonAST.{JInt, JField, JString, JObject}
import scalaz.syntax.std.option._

trait Json4sSpec[J] extends JawnDecodeSupportSpec[JValue] { self: Json4sInstances[J] =>
  import Json4sSpec._

  testJsonDecoder(json)

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
      val result = jsonOf[Int].decode(Request().withBody("42").run, strict = false)
      result.run.run must be_\/-(42)
    }

    "handle reader failures" in {
      val result = jsonOf[Int].decode(Request().withBody(""""oops"""").run, strict = false)
      result.run.run must be_-\/.like {
        case ParseFailure("Could not map JSON", _) => ok
      }
    }
  }

  "jsonExtract" should {
    implicit val formats = org.json4s.DefaultFormats

    "extract JSON from formats" in {
      val result = jsonExtract[Foo].decode(Request().withBody(JObject("bar" -> JInt(42))).run, strict = false)
      result.run.run must be_\/-(Foo(42))
    }

    "handle extract failures" in {
      val result = jsonExtract[Foo].decode(Request().withBody(""""oops"""").run, strict = false)
      result.run.run must be_-\/.like {
        case ParseFailure("Could not extract JSON", _) => ok
      }
    }
  }
}

object Json4sSpec {
  case class Foo(bar: Int)
}
