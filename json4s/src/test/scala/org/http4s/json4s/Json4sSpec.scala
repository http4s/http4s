package org.http4s
package json4s

import org.http4s.EntityEncoderSpec._
import org.http4s.Header.`Content-Type`
import org.http4s.jawn.JawnDecodeSupportSpec
import org.json4s.JValue
import org.json4s.JsonAST.{JField, JString, JObject}

trait Json4sSpec[J] extends JawnDecodeSupportSpec[JValue] { self: Json4sInstances[J] =>
  testJsonDecoder(json)

  "json encoder" should {
    val json: JValue = JObject(JField("test", JString("json4s")))

    "have json content type" in {
      jsonEncoder.headers.get(`Content-Type`) must_== Some(`Content-Type`(MediaType.`application/json`))
    }

    "write compact JSON" in {
      writeToString(json) must equal ("""{"test":"json4s"}""")
    }
  }
}
