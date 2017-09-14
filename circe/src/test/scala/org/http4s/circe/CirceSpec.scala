package org.http4s
package circe.test // Get out of circe package so we can import custom instances

import cats.effect.IO
import io.circe._
import io.circe.syntax._
import java.nio.charset.StandardCharsets
import org.http4s.Status.Ok
import org.http4s.circe._
import org.http4s.headers.`Content-Type`
import org.http4s.jawn.JawnDecodeSupportSpec
import org.specs2.specification.core.Fragment

// Originally based on ArgonautSpec
class CirceSpec extends JawnDecodeSupportSpec[Json] {
  testJsonDecoder(jsonDecoder)

  sealed case class Foo(bar: Int)
  val foo = Foo(42)
  // Beware of possible conflicting shapeless versions if using the circe-generic module
  // to derive these.
  implicit val FooDecoder: Decoder[Foo] =
    Decoder.forProduct1("bar")(Foo.apply)
  implicit val FooEncoder: Encoder[Foo] =
    Encoder.forProduct1("bar")(foo => foo.bar)

  "json encoder" should {
    val json = Json.obj("test" -> Json.fromString("CirceSupport"))

    "have json content type" in {
      jsonEncoder.headers.get(`Content-Type`) must_== Some(
        `Content-Type`(MediaType.`application/json`))
    }

    "write compact JSON" in {
      writeToString(json) must_== ("""{"test":"CirceSupport"}""")
    }

    "write JSON according to custom encoders" in {
      val custom = CirceInstances.withPrinter(Printer.spaces2)
      import custom._
      writeToString(json) must_== ("""{
          |  "test" : "CirceSupport"
          |}""".stripMargin)
    }

    "write JSON according to explicit printer" in {
      writeToString(json)(jsonEncoderWithPrinter(Printer.spaces2)) must_== ("""{
          |  "test" : "CirceSupport"
          |}""".stripMargin)
    }
  }

  "jsonEncoderOf" should {
    "have json content type" in {
      jsonEncoderOf[IO, Foo].headers.get(`Content-Type`) must_== Some(
        `Content-Type`(MediaType.`application/json`))
    }

    "write compact JSON" in {
      writeToString(foo)(jsonEncoderOf[IO, Foo]) must_== ("""{"bar":42}""")
    }

    "write JSON according to custom encoders" in {
      val custom = CirceInstances.withPrinter(Printer.spaces2)
      import custom._
      writeToString(foo)(jsonEncoderOf) must_== ("""{
          |  "bar" : 42
          |}""".stripMargin)
    }

    "write JSON according to explicit printer" in {
      writeToString(foo)(jsonEncoderWithPrinterOf(Printer.spaces2)) must_== ("""{
          |  "bar" : 42
          |}""".stripMargin)
    }
  }

  "JsonDecoder instance" should {
    "decode json from a body" in {
      val dec = JsonDecoder[IO, Foo]
      val req = Request[IO]().withBody(foo.asJson)
      req.flatMap(dec.decodeJson) must returnValue(foo)
    }

    "fail on invalid json" in {
      val dec = JsonDecoder[IO, Foo]
      val req = Request[IO]().withBody(List(13, 14).asJson)
      req.flatMap(dec.decodeJson).attempt.unsafeRunSync must beLeft
    }
  }

  "json" should {
    "handle the optionality of asNumber" in {
      // From ArgonautSpec, which tests similar things:
      // TODO Urgh.  We need to make testing these smoother.
      // https://github.com/http4s/http4s/issues/157
      def getBody(body: EntityBody[IO]): Array[Byte] = body.runLog.unsafeRunSync.toArray
      val req = Request[IO]().withBody(Json.fromDoubleOrNull(157)).unsafeRunSync
      val body = req
        .decode { json: Json =>
          Response(Ok).withBody(json.asNumber.flatMap(_.toLong).getOrElse(0L).toString)
        }
        .unsafeRunSync
        .body
      new String(getBody(body), StandardCharsets.UTF_8) must_== "157"
    }
  }

  "jsonOf" should {
    "decode JSON from a Circe decoder" in {
      val result = jsonOf[IO, Foo].decode(
        Request[IO]().withBody(Json.obj("bar" -> Json.fromDoubleOrNull(42))).unsafeRunSync,
        strict = true)
      result.value.unsafeRunSync must_== Right(Foo(42))
    }

    // https://github.com/http4s/http4s/issues/514
    Fragment.foreach(Seq("ärgerlich", """"ärgerlich"""")) { wort =>
      sealed case class Umlaut(wort: String)
      implicit val umlautDecoder: Decoder[Umlaut] = Decoder.forProduct1("wort")(Umlaut.apply)
      s"handle JSON with umlauts: $wort" >> {
        val json = Json.obj("wort" -> Json.fromString(wort))
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
      uri.asJson.as[Uri] must beRight(uri)
    }
  }
}
