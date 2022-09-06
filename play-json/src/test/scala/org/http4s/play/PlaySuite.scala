/*
 * Copyright 2018 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package play.test // Get out of play package so we can import custom instances

import _root_.play.api.libs.json._
import cats.effect.IO
import cats.effect.laws.util.TestContext
import org.http4s.headers.`Content-Type`
import org.http4s.jawn.JawnDecodeSupportSuite
import org.http4s.laws.discipline.arbitrary
import org.http4s.play._
import org.scalacheck.Prop.forAll

// Originally based on CirceSpec
class PlaySuite extends JawnDecodeSupportSuite[JsValue] {
  implicit val testContext: TestContext = TestContext()

  testJsonDecoder(jsonDecoder)

  sealed case class Foo(bar: Int)
  private val foo = Foo(42)
  implicit val format: OFormat[Foo] = Json.format[Foo]

  val json: JsValue = Json.obj("test" -> JsString("PlaySupport"))

  test("json encoder should have json content type") {
    val ct: Option[`Content-Type`] = jsonEncoder[IO].headers.get[`Content-Type`]
    assertEquals(ct, Some(`Content-Type`(MediaType.application.json)))
  }

  test("json encoder should write JSON") {
    writeToString(json).assertEquals("""{"test":"PlaySupport"}""")
  }

  test("jsonEncoderOf should have json content type") {
    val maybeHeaderT: Option[`Content-Type`] = jsonEncoderOf[IO, Foo].headers.get[`Content-Type`]
    assertEquals(maybeHeaderT, Some(`Content-Type`(MediaType.application.json)))
  }

  test("jsonEncoderOf should write compact JSON") {
    writeToString(foo)(jsonEncoderOf[IO, Foo]).assertEquals("""{"bar":42}""")
  }

  test("jsonOf should decode JSON from a Play decoder") {
    val result = jsonOf[IO, Foo]
      .decode(Request[IO]().withEntity(Json.obj("bar" -> JsNumber(42)): JsValue), strict = true)
    result.value.assertEquals(Right(Foo(42)))
  }

  property("Uri codec round trip") {
    forAll(arbitrary.createGenUri) { (uri: Uri) =>
      // Uri.renderString encode special chars in the fragment
      // and after converting the Uri to Json, the fragment will be encoded
      val preparedUri = uri.fragment.fold(uri)(f => uri.withFragment(Uri.encode(f)))
      assertEquals(Json.fromJson[Uri](Json.toJson(uri)).asOpt, Some(preparedUri))
    }
  }

  test("Message[F].decodeJson[A] should decode json from a message") {
    val req = Request[IO]().withEntity(Json.toJson(foo))
    req.decodeJson[Foo].assertEquals(foo)
  }

  test("Message[F].decodeJson[A] should fail on invalid json") {
    val req = Request[IO]().withEntity(Json.toJson(List(13, 14)))
    req.decodeJson[Foo].attempt.map(_.isLeft).assert
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
