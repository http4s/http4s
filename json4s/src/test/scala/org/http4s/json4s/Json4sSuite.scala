/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package json4s

import cats.effect.IO
import cats.syntax.all._
import org.http4s.headers.`Content-Type`
import org.http4s.jawn.JawnDecodeSupportSuite
import org.json4s.{JValue, JsonFormat}
import org.json4s.DefaultReaders._
import org.json4s.DefaultWriters._
import org.json4s.JsonAST.{JField, JInt, JObject, JString}

trait Json4sSuite[J] extends JawnDecodeSupportSuite[JValue] {
  self: Json4sInstances[J] =>
  import Json4sSuite._

  testJsonDecoder(jsonDecoder)

  val json: JValue = JObject(JField("test", JString("json4s")))

  test("json encoder should have json content type") {
    assertEquals(
      jsonEncoder[IO, JValue].headers.get(`Content-Type`),
      Some(`Content-Type`(MediaType.application.json)))
  }

  test("json encoder should write compact JSON") {
    writeToString(json).assertEquals("""{"test":"json4s"}""")
  }

  test("jsonEncoderOf should have json content type") {
    assertEquals(
      jsonEncoderOf[IO, Option[Int]].headers.get(`Content-Type`),
      Some(`Content-Type`(MediaType.application.json)))
  }

  test("jsonEncoderOf should write compact JSON with a json4s writer") {
    writeToString(42.some)(jsonEncoderOf[IO, Option[Int]]).assertEquals("""42""")
  }

  test("jsonOf should decode JSON from an json4s reader") {
    val result =
      jsonOf[IO, Int].decode(Request[IO]().withEntity("42"), strict = false)
    result.value.assertEquals(Right(42))
  }

  test("jsonOf should handle reader failures") {
    val result =
      jsonOf[IO, Int].decode(Request[IO]().withEntity(""""oops""""), strict = false)
    result.value
      .map {
        case Left(InvalidMessageBodyFailure("Could not map JSON", _)) => true
        case _ => false
      }
      .assertEquals(true)
  }

  implicit val formats = org.json4s.DefaultFormats

  test("jsonExtract should extract JSON from formats") {
    val result = jsonExtract[IO, Foo]
      .decode(Request[IO]().withEntity(JObject("bar" -> JInt(42))), strict = false)
    result.value.assertEquals(Right(Foo(42)))
  }

  test("jsonExtract should handle extract failures") {
    val result = jsonExtract[IO, Foo]
      .decode(Request[IO]().withEntity(""""oops""""), strict = false)
    result.value
      .map {
        case Left(InvalidMessageBodyFailure("Could not extract JSON", _)) => true
        case _ => false
      }
      .assertEquals(true)
  }

  test("JsonFormat[Uri] should round trip") {
    // TODO would benefit from Arbitrary[Uri]
    val uri = Uri.uri("http://www.example.com/")
    val format = implicitly[JsonFormat[Uri]]
    assertEquals(format.read(format.write(uri)), uri)
  }

  test("Message[F].decodeJson[A] should decode json from a message") {
    val req = Request[IO]()
      .withEntity("42")
      .withContentType(`Content-Type`(MediaType.application.json))
    req.decodeJson[Option[Int]].assertEquals(Some(42))
  }

  test("Message[F].decodeJson[A] should fail on invalid json") {
    val req = Request[IO]()
      .withEntity("not a number")
      .withContentType(`Content-Type`(MediaType.application.json))
    req.decodeJson[Int].attempt.map(_.isLeft).assertEquals(true)
  }
}

object Json4sSuite {
  final case class Foo(bar: Int)
}
