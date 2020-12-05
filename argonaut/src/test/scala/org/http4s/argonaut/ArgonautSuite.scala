/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package argonaut.test // Get out of argonaut package so we can import custom instances

import _root_.argonaut._
import cats.effect.IO
import cats.syntax.all._
import java.nio.charset.StandardCharsets
import jawn.JawnDecodeSupportSuite
import org.http4s.Status.Ok
import org.http4s.argonaut._
import org.http4s.headers.`Content-Type`

class ArgonautSuite extends JawnDecodeSupportSuite[Json] with Argonauts {
  val ArgonautInstancesWithCustomErrors = ArgonautInstances.builder
    .withEmptyBodyMessage(MalformedMessageBodyFailure("Custom Invalid JSON: empty body"))
    .withParseExceptionMessage(_ => MalformedMessageBodyFailure("Custom Invalid JSON"))
    .withJsonDecodeError((json, message, history) =>
      InvalidMessageBodyFailure(
        s"Custom Could not decode JSON: $json, error: $message, cursor: $history"))
    .build

  testJsonDecoder(jsonDecoder)
  testJsonDecoderError(ArgonautInstancesWithCustomErrors.jsonDecoder)(
    emptyBody = { case MalformedMessageBodyFailure("Custom Invalid JSON: empty body", _) => true },
    parseError = { case MalformedMessageBodyFailure("Custom Invalid JSON", _) => true }
  )

  sealed case class Foo(bar: Int)
  val foo = Foo(42)
  implicit val FooCodec = CodecJson.derive[Foo]

  val json = Json("test" -> jString("ArgonautSupport"))

  test("json encoder should have json content type") {
    assertEquals(
      jsonEncoder.headers.get(`Content-Type`),
      Some(`Content-Type`(MediaType.application.json)))
  }

  test("json encoder should write compact JSON") {
    writeToString(json).assertEquals("""{"test":"ArgonautSupport"}""")
  }

  test("json encoder should write JSON according to custom encoders") {
    val custom = ArgonautInstances.withPrettyParams(PrettyParams.spaces2).build
    import custom._
    writeToString(json).assertEquals(("""{
                                     |  "test" : "ArgonautSupport"
                                     |}""".stripMargin))
  }

  test("json encoder should write JSON according to explicit printer") {
    writeToString(json)(jsonEncoderWithPrettyParams(PrettyParams.spaces2)).assertEquals(("""{
                                                                                        |  "test" : "ArgonautSupport"
                                                                                        |}""".stripMargin))
  }

  test("jsonEncoderOfhave json content type") {
    assertEquals(
      jsonEncoderOf[IO, Foo].headers.get(`Content-Type`),
      Some(`Content-Type`(MediaType.application.json)))
  }

  test("jsonEncoderOf should write compact JSON") {
    writeToString(foo)(jsonEncoderOf[IO, Foo]).assertEquals("""{"bar":42}""")
  }

  test("jsonEncoderOf should write JSON according to custom encoders") {
    val custom = ArgonautInstances.withPrettyParams(PrettyParams.spaces2).build
    import custom._
    writeToString(foo)(jsonEncoderOf).assertEquals(("""{
                                                   |  "bar" : 42
                                                   |}""".stripMargin))
  }

  test("write JSON according to explicit printer") {
    writeToString(foo)(jsonEncoderWithPrinterOf(PrettyParams.spaces2)).assertEquals(("""{
                                                                                    |  "bar" : 42
                                                                                    |}""".stripMargin))
  }

  test("json should handle the optionality of jNumber") {
    // TODO Urgh.  We need to make testing these smoother.
    // https://github.com/http4s/http4s/issues/157
    def getBody(body: EntityBody[IO]): IO[Array[Byte]] = body.compile.to(Array)
    val req = Request[IO]().withEntity(jNumberOrNull(157))
    req
      .decode { (json: Json) =>
        Response[IO](Ok).withEntity(json.number.flatMap(_.toLong).getOrElse(0L).toString).pure[IO]
      }
      .map(_.body)
      .flatMap(getBody)
      .map(new String(_, StandardCharsets.UTF_8))
      .assertEquals("157")
  }

  test("jsonOf should decode JSON from an Argonaut decoder") {
    jsonOf[IO, Foo]
      .decode(Request[IO]().withEntity(jObjectFields("bar" -> jNumberOrNull(42))), strict = true)
      .value
      .assertEquals(Right(Foo(42)))
  }

  // https://github.com/http4s/http4s/issues/514
  sealed case class Umlaut(wort: String)
  implicit val codec = CodecJson.derive[Umlaut]
  test("json should handle JSON with umlauts") {
    List("ärgerlich", """"ärgerlich"""").traverse { wort =>
      val json = Json("wort" -> jString(wort))
      val result =
        jsonOf[IO, Umlaut].decode(Request[IO]().withEntity(json), strict = true)
      result.value.assertEquals(Right(Umlaut(wort)))
    }
  }

  test("json shouldfail with custom message from an Argonaut decoder") {
    val result = ArgonautInstancesWithCustomErrors
      .jsonOf[IO, Foo]
      .decode(Request[IO]().withEntity(jObjectFields("bar1" -> jNumberOrNull(42))), strict = true)
    result.value.assertEquals(Left(InvalidMessageBodyFailure(
      "Custom Could not decode JSON: {\"bar1\":42.0}, error: Attempt to decode value on failed cursor., cursor: CursorHistory(List(El(CursorOpDownField(bar),false)))")))
  }

  test("Uri codec should round trip") {
    // TODO would benefit from Arbitrary[Uri]
    val uri = Uri.uri("http://www.example.com/")
    assertEquals(uri.asJson.as[Uri].result, Right(uri))
  }

  test("Message[F].decodeJson[A] should decode json from a message") {
    val req = Request[IO]().withEntity(foo.asJson)
    req.decodeJson[Foo].assertEquals(foo)
  }

  test("Message[F].decodeJson[A] should fail on invalid json") {
    val req = Request[IO]().withEntity(List(13, 14).asJson)
    req.decodeJson[Foo].attempt.map(_.isLeft).assertEquals(true)
  }
}
