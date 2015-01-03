package org.http4s
package argonaut

import java.nio.charset.StandardCharsets

import _root_.argonaut._
import org.http4s.Header.`Content-Type`
import org.http4s.jawn.JawnDecodeSupportSpec
import org.http4s.EntityEncoderSpec.writeToString
import Status.Ok

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

  "json decoder" should {
    "handles the optionality of jNumber" in {
      // https://github.com/http4s/http4s/issues/157
      // TODO Urgh.  We need to make testing these smoother.
      def getBody(body: EntityBody): Array[Byte] = body.runLog.run.reduce(_ ++ _).toArray
      val req = Request().withBody(jNumberOrNull(157))
      val body = json(req.run) { json => Response(Ok).withBody(json.number.flatMap(_.toLong).getOrElse(0L).toString) }.run.body
      new String(getBody(body), StandardCharsets.UTF_8) must_== "157"
    }
  }
}
