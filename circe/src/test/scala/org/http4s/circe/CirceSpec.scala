package org.http4s
package circe.test // Get out of circe package so we can import custom instances

import java.nio.charset.StandardCharsets

import io.circe._
import io.circe.syntax._
import org.http4s.circe._
import org.http4s.headers.`Content-Type`
import org.http4s.jawn.JawnDecodeSupportSpec
import Status.Ok
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
    Encoder.forProduct1("bar")(foo => (foo.bar))

  "json encoder" should {
    val json = Json.obj("test" -> Json.fromString("CirceSupport"))

    "have json content type" in {
      jsonEncoder.headers.get(`Content-Type`) must_== Some(`Content-Type`(MediaType.`application/json`))
    }

    "write compact JSON" in {
      writeToString(json) must_== ("""{"test":"CirceSupport"}""")
    }

    "write JSON according to custom encoders" in {
      val custom = CirceInstances.withPrinter(Printer.spaces2)
      import custom._
      writeToString(json) must_== (
        """{
          |  "test" : "CirceSupport"
          |}""".stripMargin)
    }

    "write JSON according to explicit printer" in {
      writeToString(json)(jsonEncoderWithPrinter(Printer.spaces2)) must_== (
        """{
          |  "test" : "CirceSupport"
          |}""".stripMargin)
    }
  }

  "jsonEncoderOf" should {
    "have json content type" in {
      jsonEncoderOf[Foo].headers.get(`Content-Type`) must_== Some(`Content-Type`(MediaType.`application/json`))
    }

    "write compact JSON" in {
      writeToString(foo)(jsonEncoderOf[Foo]) must_== ("""{"bar":42}""")
    }

    "write JSON according to custom encoders" in {
      val custom = CirceInstances.withPrinter(Printer.spaces2)
      import custom._
      writeToString(foo)(jsonEncoderOf) must_== (
        """{
          |  "bar" : 42
          |}""".stripMargin)
    }

    "write JSON according to explicit printer" in {
      writeToString(foo)(jsonEncoderWithPrinterOf(Printer.spaces2)) must_== (
        """{
          |  "bar" : 42
          |}""".stripMargin)
    }
  }

  "json" should {
    "handle the optionality of asNumber" in {
      // From ArgonautSpec, which tests similar things:
      // TODO Urgh.  We need to make testing these smoother.
      // https://github.com/http4s/http4s/issues/157
      def getBody(body: EntityBody): Array[Byte] = body.runLog.unsafeRun.toArray
      val req = Request().withBody(Json.fromDoubleOrNull(157)).unsafeRun
      val body = req.decode { json: Json => Response(Ok).withBody(json.asNumber.flatMap(_.toLong).getOrElse(0L).toString)}.unsafeRun.body
      new String(getBody(body), StandardCharsets.UTF_8) must_== "157"
    }
  }

  "jsonOf" should {
    "decode JSON from a Circe decoder" in {
      val result = jsonOf[Foo].decode(Request().withBody(Json.obj("bar" -> Json.fromDoubleOrNull(42))).unsafeRun, strict = true)
      result.value.unsafeRun must_== Right(Foo(42))
    }

    // https://github.com/http4s/http4s/issues/514
    Fragment.foreach(Seq("ärgerlich", """"ärgerlich"""")) { wort =>
      sealed case class Umlaut(wort: String)
      implicit val umlautDecoder =
        Decoder.forProduct1("wort")(Umlaut.apply)
      s"handle JSON with umlauts: $wort" >> {
        val json = Json.obj("wort" -> Json.fromString(wort))
        val result = jsonOf[Umlaut].decode(Request().withBody(json).unsafeRun, strict = true)
        result.value.unsafeRun must_== Right(Umlaut(wort))
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
