package org.http4s.argonaut

import _root_.argonaut._
import org.http4s.Header.`Content-Type`
import org.http4s.jawn.JawnDecodeSupportSpec
import org.http4s.MediaType
import org.http4s.EntityEncoderSpec.writeToString

class ArgonautSpec extends JawnDecodeSupportSpec[Json] with Argonauts {
  testJsonDecoder(json)

  "writing JSON" should {
    val json = Json("test" -> jString("ArgonautSupport"))

    "have json content type" in {
      jsonEncoder.headers.get(`Content-Type`) must_== Some(`Content-Type`(MediaType.`application/json`))
    }

    "write compact JSON" in {
      writeToString(json) must_== """{"test":"ArgonautSupport"}"""
    }
  }
}
