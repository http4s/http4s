package org.http4s
package json4s

import org.http4s.Header.`Content-Type`
import org.http4s.jawn.JawnDecodeSupportSpec
import org.json4s.JsonAST.{JField, JString, JObject, JValue}
import EntityEncoderSpec._

class Json4sSpec extends JawnDecodeSupportSpec[JValue] {
  testJsonDecoder(json)

  "writing JSON" should {
    val json: JValue = JObject(JField("test", JString("json4s")))

    implicit val jsonMethods = org.json4s.jackson.JsonMethods

    "have json content type" in {
      jsonEncoder.headers.get(`Content-Type`) must_== Some(`Content-Type`(MediaType.`application/json`))
    }

    "write compact JSON" in {
      writeToString(json) must_== """{"test":"json4s"}"""
    }
  }
}
