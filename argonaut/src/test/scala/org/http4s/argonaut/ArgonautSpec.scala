package org.http4s
package argonaut.test // Get out of argonaut package so we can import custom instances

import _root_.argonaut._
import cats.effect.IO
import java.nio.charset.StandardCharsets
import org.http4s.Status.Ok
import org.http4s.argonaut._
import org.http4s.headers.`Content-Type`
import org.http4s.jawn.JawnDecodeSupportSpec
import org.specs2.specification.core.Fragment

class ArgonautSpec extends JawnDecodeSupportSpec[Json] with Argonauts {
  testJsonDecoder(jsonDecoder)

  sealed case class Foo(bar: Int)
  val foo = Foo(42)
  implicit val FooCodec = CodecJson.derive[Foo]

  "json encoder" should {
    val json = Json("test" -> jString("ArgonautSupport"))

    "have json content type" in {
      jsonEncoder.headers.get(`Content-Type`) must_== Some(
        `Content-Type`(MediaType.`application/json`, Charset.`UTF-8`))
    }

    "write compact JSON" in {
      writeToString(json) must_== ("""{"test":"ArgonautSupport"}""")
    }

    "write JSON according to custom encoders" in {
      val custom = ArgonautInstances.withPrettyParams(PrettyParams.spaces2)
      import custom._
      writeToString(json) must_== ("""{
          |  "test" : "ArgonautSupport"
          |}""".stripMargin)
    }

    "write JSON according to explicit printer" in {
      writeToString(json)(jsonEncoderWithPrettyParams(PrettyParams.spaces2)) must_== ("""{
          |  "test" : "ArgonautSupport"
          |}""".stripMargin)
    }
  }

  "jsonEncoderOf" should {
    "have json content type" in {
      jsonEncoderOf[IO, Foo].headers.get(`Content-Type`) must_== Some(
        `Content-Type`(MediaType.`application/json`, Charset.`UTF-8`))
    }

    "write compact JSON" in {
      writeToString(foo)(jsonEncoderOf[IO, Foo]) must_== ("""{"bar":42}""")
    }

    "write JSON according to custom encoders" in {
      val custom = ArgonautInstances.withPrettyParams(PrettyParams.spaces2)
      import custom._
      writeToString(foo)(jsonEncoderOf) must_== ("""{
          |  "bar" : 42
          |}""".stripMargin)
    }

    "write JSON according to explicit printer" in {
      writeToString(foo)(jsonEncoderWithPrinterOf(PrettyParams.spaces2)) must_== ("""{
          |  "bar" : 42
          |}""".stripMargin)
    }
  }

  "json" should {
    "handle the optionality of jNumber" in {
      // TODO Urgh.  We need to make testing these smoother.
      // https://github.com/http4s/http4s/issues/157
      def getBody(body: EntityBody[IO]): Array[Byte] = body.runLog.unsafeRunSync.toArray
      val req = Request[IO]().withBody(jNumberOrNull(157)).unsafeRunSync
      val body = req
        .decode { json: Json =>
          Response(Ok).withBody(json.number.flatMap(_.toLong).getOrElse(0L).toString)
        }
        .unsafeRunSync
        .body
      new String(getBody(body), StandardCharsets.UTF_8) must_== "157"
    }
  }

  "jsonOf" should {
    "decode JSON from an Argonaut decoder" in {
      val result = jsonOf[IO, Foo].decode(
        Request[IO]().withBody(jObjectFields("bar" -> jNumberOrNull(42))).unsafeRunSync,
        strict = true)
      result.value.unsafeRunSync must beRight(Foo(42))
    }

    // https://github.com/http4s/http4s/issues/514
    Fragment.foreach(Seq("ärgerlich", """"ärgerlich"""")) { wort =>
      sealed case class Umlaut(wort: String)
      implicit val codec = CodecJson.derive[Umlaut]
      s"handle JSON with umlauts: $wort" >> {
        val json = Json("wort" -> jString(wort))
        val result =
          jsonOf[IO, Umlaut].decode(Request[IO]().withBody(json).unsafeRunSync, strict = true)
        result.value.unsafeRunSync must_== Right(Umlaut(wort))
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

  "Message[F].decodeJson[A]" should {
    "decode json from a message" in {
      val req = Request[IO]().withBody(foo.asJson)
      req.flatMap(_.decodeJson[Foo]) must returnValue(foo)
    }

    "fail on invalid json" in {
      val req = Request[IO]().withBody(List(13, 14).asJson)
      req.flatMap(_.decodeJson[Foo]).attempt.unsafeRunSync must beLeft
    }
  }
}
