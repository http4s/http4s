package org.http4s
package argonaut.test // Get out of argonaut package so we can import custom instances

import java.nio.charset.StandardCharsets

import _root_.argonaut._
import org.http4s.MediaType._
import org.http4s.argonaut._
import org.http4s.headers.`Content-Type`
import org.http4s.jawn.JawnDecodeSupportSpec
import org.specs2.specification.core.Fragment
import Status.Ok

class ArgonautSpec extends JawnDecodeSupportSpec[Json] with Argonauts {
  testJsonDecoder(jsonDecoder)

  sealed case class Foo(bar: Int)
  val foo = Foo(42)
  implicit val FooCodec = CodecJson.derive[Foo]

  "json encoder" should {
    val json = Json("test" -> jString("ArgonautSupport"))

    "have json content type" in {
      jsonEncoder.headers.get(`Content-Type`) must_== Some(`Content-Type`(MediaType.`application/json`, Charset.`UTF-8`))
    }

    "write compact JSON" in {
      writeToString(json) must_== ("""{"test":"ArgonautSupport"}""")
    }


    "write JSON according to custom encoders" in {
      val custom = ArgonautInstances.withPrettyParams(PrettyParams.spaces2)
      import custom._
      writeToString(json) must_== (
        """{
          |  "test" : "ArgonautSupport"
          |}""".stripMargin)
    }

    "write JSON according to explicit printer" in {
      writeToString(json)(jsonEncoderWithPrettyParams(PrettyParams.spaces2)) must_== (
        """{
          |  "test" : "ArgonautSupport"
          |}""".stripMargin)
    }
  }

  "jsonEncoderOf" should {
    "have json content type" in {
      jsonEncoderOf[Foo].headers.get(`Content-Type`) must_== Some(`Content-Type`(MediaType.`application/json`, Charset.`UTF-8`))
    }

    "write compact JSON" in {
      writeToString(foo)(jsonEncoderOf[Foo]) must_== ("""{"bar":42}""")
    }

    "write JSON according to custom encoders" in {
      val custom = ArgonautInstances.withPrettyParams(PrettyParams.spaces2)
      import custom._
      writeToString(foo)(jsonEncoderOf) must_== (
        """{
          |  "bar" : 42
          |}""".stripMargin)
    }

    "write JSON according to explicit printer" in {
      writeToString(foo)(jsonEncoderWithPrinterOf(PrettyParams.spaces2)) must_== (
        """{
          |  "bar" : 42
          |}""".stripMargin)
    }
  }

  "json" should {
    "handle the optionality of jNumber" in {
      // TODO Urgh.  We need to make testing these smoother.
      // https://github.com/http4s/http4s/issues/157
      def getBody(body: EntityBody): Array[Byte] = body.runLog.run.reduce(_ ++ _).toArray
      val req = Request().withBody(jNumberOrNull(157)).run
      val body = req.decode { json: Json => Response(Ok).withBody(json.number.flatMap(_.toLong).getOrElse(0L).toString)}.run.body
      new String(getBody(body), StandardCharsets.UTF_8) must_== "157"
    }
  }

  "jsonOf" should {
    "decode JSON from an Argonaut decoder" in {
      val result = jsonOf[Foo].decode(Request().withBody(jObjectFields("bar" -> jNumberOrNull(42))).run, strict = true)
      result.run.run must be_\/-(Foo(42))
    }

    // https://github.com/http4s/http4s/issues/514
    Fragment.foreach(Seq("ärgerlich", """"ärgerlich"""")) { wort =>
      sealed case class Umlaut(wort: String)
      implicit val codec = CodecJson.derive[Umlaut]
      val umlautDecoder = jsonOf[Umlaut]
      s"handle JSON with umlauts: $wort" >> {
        val json = Json("wort" -> jString(wort))
        val result = jsonOf[Umlaut].decode(Request().withBody(json).run, strict = true)
        result.run.run must be_\/-(Umlaut(wort))
      }
    }
  }

  "Uri codec" should {
    "round trip" in {
      // TODO would benefit from Arbitrary[Uri]
      val uri = Uri.uri("http://www.example.com/")
      uri.asJson.as[Uri].result must beRight(uri)
    }
  }
}
