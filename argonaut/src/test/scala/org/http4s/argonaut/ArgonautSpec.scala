package org.http4s
package argonaut.test // Get out of argonaut package so we can import custom instances

import _root_.argonaut._
import cats.effect.IO
import cats.syntax.applicative._
import java.nio.charset.StandardCharsets

import org.http4s.Status.Ok
import org.http4s.argonaut._
import org.http4s.headers.`Content-Type`
import jawn.JawnDecodeSupportSpec
import org.specs2.specification.core.Fragment

class ArgonautSpec extends JawnDecodeSupportSpec[Json] with Argonauts {
  val ArgonautInstancesWithCustomErrors = ArgonautInstances.builder
    .withEmptyBodyMessage(MalformedMessageBodyFailure("Custom Invalid JSON: empty body"))
    .withParseExceptionMessage(_ => MalformedMessageBodyFailure("Custom Invalid JSON"))
    .withJsonDecodeError((json, message, history) =>
      InvalidMessageBodyFailure(
        s"Custom Could not decode JSON: $json, error: $message, cursor: $history"))
    .build

  testJsonDecoder(jsonDecoder)
  testJsonDecoderError(ArgonautInstancesWithCustomErrors.jsonDecoder)(
    emptyBody = { case MalformedMessageBodyFailure("Custom Invalid JSON: empty body", _) => ok },
    parseError = { case MalformedMessageBodyFailure("Custom Invalid JSON", _) => ok }
  )

  sealed case class Foo(bar: Int)
  val foo = Foo(42)
  implicit val FooCodec = CodecJson.derive[Foo]

  "json encoder" should {
    val json = Json("test" -> jString("ArgonautSupport"))

    "have json content type" in {
      jsonEncoder.headers.get(`Content-Type`) must_== Some(
        `Content-Type`(MediaType.application.json))
    }

    "write compact JSON" in {
      writeToString(json) must_== ("""{"test":"ArgonautSupport"}""")
    }

    "write JSON according to custom encoders" in {
      val custom = ArgonautInstances.withPrettyParams(PrettyParams.spaces2).build
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
        `Content-Type`(MediaType.application.json))
    }

    "write compact JSON" in {
      writeToString(foo)(jsonEncoderOf[IO, Foo]) must_== ("""{"bar":42}""")
    }

    "write JSON according to custom encoders" in {
      val custom = ArgonautInstances.withPrettyParams(PrettyParams.spaces2).build
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
      def getBody(body: EntityBody[IO]): Array[Byte] = body.compile.toVector.unsafeRunSync.toArray
      val req = Request[IO]().withEntity(jNumberOrNull(157))
      val body = req
        .decode { json: Json =>
          Response[IO](Ok).withEntity(json.number.flatMap(_.toLong).getOrElse(0L).toString).pure[IO]
        }
        .unsafeRunSync
        .body
      new String(getBody(body), StandardCharsets.UTF_8) must_== "157"
    }
  }

  "jsonOf" should {
    "decode JSON from an Argonaut decoder" in {
      val result = jsonOf[IO, Foo]
        .decode(Request[IO]().withEntity(jObjectFields("bar" -> jNumberOrNull(42))), strict = true)
      result.value.unsafeRunSync must beRight(Foo(42))
    }

    // https://github.com/http4s/http4s/issues/514
    Fragment.foreach(Seq("ärgerlich", """"ärgerlich"""")) { wort =>
      sealed case class Umlaut(wort: String)
      implicit val codec = CodecJson.derive[Umlaut]
      s"handle JSON with umlauts: $wort" >> {
        val json = Json("wort" -> jString(wort))
        val result =
          jsonOf[IO, Umlaut].decode(Request[IO]().withEntity(json), strict = true)
        result.value.unsafeRunSync must_== Right(Umlaut(wort))
      }
    }

    "fail with custom message from an Argonaut decoder" in {
      val result = ArgonautInstancesWithCustomErrors
        .jsonOf[IO, Foo]
        .decode(Request[IO]().withEntity(jObjectFields("bar1" -> jNumberOrNull(42))), strict = true)
      result.value.unsafeRunSync must beLeft(InvalidMessageBodyFailure(
        "Custom Could not decode JSON: {\"bar1\":42.0}, error: Attempt to decode value on failed cursor., cursor: CursorHistory(List(El(CursorOpDownField(bar),false)))"))
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
      val req = Request[IO]().withEntity(foo.asJson)
      req.decodeJson[Foo] must returnValue(foo)
    }

    "fail on invalid json" in {
      val req = Request[IO]().withEntity(List(13, 14).asJson)
      req.decodeJson[Foo].attempt.unsafeRunSync must beLeft
    }
  }
}
