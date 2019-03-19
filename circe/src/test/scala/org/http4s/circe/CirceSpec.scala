package org.http4s
package circe.test // Get out of circe package so we can import custom instances

import cats.effect.IO
import cats.effect.laws.util.TestContext
import cats.syntax.applicative._
import cats.syntax.foldable._
import io.circe._
import io.circe.syntax._
import io.circe.testing.instances._
import java.nio.charset.StandardCharsets
import cats.data.NonEmptyList
import org.http4s.Status.Ok
import org.http4s.circe._
import org.http4s.headers.`Content-Type`
import org.http4s.jawn.JawnDecodeSupportSpec
import org.http4s.testing.EntityCodecTests
import org.specs2.specification.core.Fragment

// Originally based on ArgonautSpec
class CirceSpec extends JawnDecodeSupportSpec[Json] {
  implicit val testContext = TestContext()

  val CirceInstancesWithCustomErrors = CirceInstances.builder
    .withEmptyBodyMessage(MalformedMessageBodyFailure("Custom Invalid JSON: empty body"))
    .withJawnParseExceptionMessage(_ => MalformedMessageBodyFailure("Custom Invalid JSON jawn"))
    .withCirceParseExceptionMessage(_ => MalformedMessageBodyFailure("Custom Invalid JSON circe"))
    .withJsonDecodeError({ (json, failures) =>
      val failureStr = failures.mkString_("", ", ", "")
      InvalidMessageBodyFailure(
        s"Custom Could not decode JSON: ${json.noSpaces}, errors: $failureStr")
    })
    .build

  testJsonDecoder(jsonDecoder)
  testJsonDecoderError(CirceInstancesWithCustomErrors.jsonDecoderIncremental)(
    emptyBody = { case MalformedMessageBodyFailure("Custom Invalid JSON: empty body", _) => ok },
    parseError = { case MalformedMessageBodyFailure("Custom Invalid JSON jawn", _) => ok }
  )
  testJsonDecoderError(CirceInstancesWithCustomErrors.jsonDecoderByteBuffer)(
    emptyBody = { case MalformedMessageBodyFailure("Custom Invalid JSON: empty body", _) => ok },
    parseError = { case MalformedMessageBodyFailure("Custom Invalid JSON circe", _) => ok }
  )

  sealed case class Foo(bar: Int)
  val foo = Foo(42)
  // Beware of possible conflicting shapeless versions if using the circe-generic module
  // to derive these.
  implicit val FooDecoder: Decoder[Foo] =
    Decoder.forProduct1("bar")(Foo.apply)
  implicit val FooEncoder: Encoder[Foo] =
    Encoder.forProduct1("bar")(foo => foo.bar)

  sealed case class Bar(a: Int, b: String)
  implicit val barDecoder: Decoder[Bar] =
    Decoder.forProduct2("a", "b")(Bar.apply)
  implicit val barEncoder: Encoder[Bar] =
    Encoder.forProduct2("a", "b")(bar => (bar.a, bar.b))

  "json encoder" should {
    val json = Json.obj("test" -> Json.fromString("CirceSupport"))

    "have json content type" in {
      jsonEncoder[IO].headers.get(`Content-Type`) must_== Some(
        `Content-Type`(MediaType.application.json))
    }

    "write compact JSON" in {
      writeToString(json) must_== ("""{"test":"CirceSupport"}""")
    }

    "write JSON according to custom encoders" in {
      val custom = CirceInstances.withPrinter(Printer.spaces2).build
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
        `Content-Type`(MediaType.application.json))
    }

    "write compact JSON" in {
      writeToString(foo)(jsonEncoderOf[IO, Foo]) must_== ("""{"bar":42}""")
    }

    "write JSON according to custom encoders" in {
      val custom = CirceInstances.withPrinter(Printer.spaces2).build
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

  "json" should {
    "handle the optionality of asNumber" in {
      // From ArgonautSpec, which tests similar things:
      // TODO Urgh.  We need to make testing these smoother.
      // https://github.com/http4s/http4s/issues/157
      def getBody(body: EntityBody[IO]): Array[Byte] = body.compile.toVector.unsafeRunSync.toArray
      val req = Request[IO]().withEntity(Json.fromDoubleOrNull(157))
      val body = req
        .decode { json: Json =>
          Response[IO](Ok)
            .withEntity(json.asNumber.flatMap(_.toLong).getOrElse(0L).toString)
            .pure[IO]
        }
        .unsafeRunSync
        .body
      new String(getBody(body), StandardCharsets.UTF_8) must_== "157"
    }
  }

  "jsonOf" should {
    "decode JSON from a Circe decoder" in {
      val result = jsonOf[IO, Foo]
        .decode(
          Request[IO]().withEntity(Json.obj("bar" -> Json.fromDoubleOrNull(42))),
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
          jsonOf[IO, Umlaut].decode(Request[IO]().withEntity(json), strict = true)
        result.value.unsafeRunSync must_== Right(Umlaut(wort))
      }
    }

    "fail with custom message from a decoder" in {
      val result = CirceInstancesWithCustomErrors
        .jsonOf[IO, Bar]
        .decode(Request[IO]().withEntity(Json.obj("bar1" -> Json.fromInt(42))), strict = true)
      result.value.unsafeRunSync must beLeft(InvalidMessageBodyFailure(
        "Custom Could not decode JSON: {\"bar1\":42}, errors: DecodingFailure at .a: Attempt to decode value on failed cursor"))
    }
  }

  "accumulatingJsonOf" should {
    "decode JSON from a Circe decoder" in {
      val result = accumulatingJsonOf[IO, Foo]
        .decode(
          Request[IO]().withEntity(Json.obj("bar" -> Json.fromDoubleOrNull(42))),
          strict = true)
      result.value.unsafeRunSync must_== Right(Foo(42))
    }

    "return an InvalidMessageBodyFailure with a list of failures on invalid JSON messages" in {
      val json = Json.obj("a" -> Json.fromString("sup"), "b" -> Json.fromInt(42))
      val result = accumulatingJsonOf[IO, Bar]
        .decode(Request[IO]().withEntity(json), strict = true)
      result.value.unsafeRunSync must beLike {
        case Left(InvalidMessageBodyFailure(_, Some(DecodingFailures(NonEmptyList(_, _))))) => ok
      }
    }

    "fail with custom message from a decoder" in {
      val result = CirceInstancesWithCustomErrors
        .accumulatingJsonOf[IO, Bar]
        .decode(Request[IO]().withEntity(Json.obj("bar1" -> Json.fromInt(42))), strict = true)
      result.value.unsafeRunSync must beLeft(InvalidMessageBodyFailure(
        "Custom Could not decode JSON: {\"bar1\":42}, errors: DecodingFailure at .a: Attempt to decode value on failed cursor, DecodingFailure at .b: Attempt to decode value on failed cursor"))
    }
  }

  "Uri codec" should {
    "round trip" in {
      // TODO would benefit from Arbitrary[Uri]
      val uri = Uri.uri("http://www.example.com/")
      uri.asJson.as[Uri] must beRight(uri)
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

  "CirceEntityEncDec" should {
    "decode json without defining EntityDecoder" in {
      import org.http4s.circe.CirceEntityDecoder._
      val request = Request[IO]().withEntity(Json.obj("bar" -> Json.fromDoubleOrNull(42)))
      val result = request.attemptAs[Foo]
      result.value.unsafeRunSync must_== Right(Foo(42))
    }

    "encode without defining EntityEncoder using default printer" in {
      import org.http4s.circe.CirceEntityEncoder._
      writeToString(foo) must_== """{"bar":42}"""
    }
  }

  "CirceInstances.builder" should {
    "handle JSON parsing errors" in {
      val req = Request[IO]()
        .withEntity("broken json")
        .withContentType(`Content-Type`(MediaType.application.json))

      val decoder = CirceInstances.builder.build.jsonOf[IO, Int]
      val result = decoder.decode(req, true).value.unsafeRunSync

      result must beLeft.like {
        case _: MalformedMessageBodyFailure => ok
      }
    }

    "handle JSON decoding errors" in {
      val req = Request[IO]()
        .withEntity(Json.obj())

      val decoder = CirceInstances.builder.build.jsonOf[IO, Int]
      val result = decoder.decode(req, true).value.unsafeRunSync

      result must beLeft.like {
        case _: InvalidMessageBodyFailure => ok
      }
    }
  }

  checkAll("EntityCodec[IO, Json]", EntityCodecTests[IO, Json].entityCodec)
}
