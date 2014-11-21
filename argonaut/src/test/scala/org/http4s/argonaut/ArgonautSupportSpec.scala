package org.http4s.argonaut

import argonaut._
import org.http4s.Header.`Content-Type`
import org.specs2.mutable.Specification
import org.http4s.MediaType
import org.http4s.Charset._
import org.http4s.WritableSpec.writeToString

class ArgonautSupportSpec extends Specification with ArgonautSupport {
  "writing JSON" should {
    val json = Json("test" -> jString("ArgonautSupport"))

    "have json content type" in {
      jsonWritable.headers.get(`Content-Type`) must beSome(`Content-Type`(MediaType.`application/json`, `UTF-8`))
    }

    "write compact JSON" in {
      writeToString(json) must_== """{"test":"ArgonautSupport"}"""
    }
  }
}
