/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package play.test // Get out of play package so we can import custom instances

import _root_.play.api.libs.json._
import cats.effect.IO
import cats.effect.laws.util.TestContext
import org.http4s.headers.`Content-Type`
import org.http4s.jawn.JawnDecodeSupportSuite
import org.http4s.play._

// Originally based on CirceSpec
class PlaySuite extends JawnDecodeSupportSuite[JsValue] {
  implicit val testContext = TestContext()

  testJsonDecoder(jsonDecoder)

  sealed case class Foo(bar: Int)
  val foo = Foo(42)
  implicit val format: OFormat[Foo] = Json.format[Foo]

  val json: JsValue = Json.obj("test" -> JsString("PlaySupport"))

  test("json encoder should have json content type") {
    assertEquals(
      jsonEncoder.headers.get(`Content-Type`),
      Some(`Content-Type`(MediaType.application.json)))
  }

  test("json encoder should write JSON") {
    writeToString(json).assertEquals("""{"test":"PlaySupport"}""")
  }

  test("jsonEncoderOf should have json content type") {
    assertEquals(
      jsonEncoderOf[IO, Foo].headers.get(`Content-Type`),
      Some(`Content-Type`(MediaType.application.json)))
  }

  test("jsonEncoderOf should write compact JSON") {
    writeToString(foo)(jsonEncoderOf[IO, Foo]).assertEquals("""{"bar":42}""")
  }

  test("jsonOf should decode JSON from a Play decoder") {
    val result = jsonOf[IO, Foo]
      .decode(Request[IO]().withEntity(Json.obj("bar" -> JsNumber(42)): JsValue), strict = true)
    result.value.assertEquals(Right(Foo(42)))
  }

  test("Uri codec should round trip") {
    // TODO would benefit from Arbitrary[Uri]
    val uri = Uri.uri("http://www.example.com/")

    assertEquals(Json.fromJson[Uri](Json.toJson(uri)).asOpt, Some(uri))
  }

  test("Message[F].decodeJson[A] should decode json from a message") {
    val req = Request[IO]().withEntity(Json.toJson(foo))
    req.decodeJson[Foo].assertEquals(foo)
  }

  test("Message[F].decodeJson[A] should fail on invalid json") {
    val req = Request[IO]().withEntity(Json.toJson(List(13, 14)))
    req.decodeJson[Foo].attempt.map(_.isLeft).assertEquals(true)
  }

  test("PlayEntityCodec should decode json without defining EntityDecoder") {
    import org.http4s.play.PlayEntityDecoder._
    val request = Request[IO]().withEntity(Json.obj("bar" -> JsNumber(42)): JsValue)
    val result = request.as[Foo]
    result.assertEquals(Foo(42))
  }

  test("PlayEntityCodec should encode without defining EntityEncoder using default printer") {
    import org.http4s.play.PlayEntityEncoder._
    writeToString(foo).assertEquals("""{"bar":42}""")
  }
}
