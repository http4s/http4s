/*
 * Copyright 2014 http4s.org
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
package twirl

import cats.effect.IO
import org.http4s.Status.Ok
import org.http4s.headers.`Content-Type`
import org.scalacheck.effect.PropF
import org.scalacheck.{Arbitrary, Gen, Prop}
import play.twirl.api.{Html, JavaScript, Txt, Xml}

class TwirlSuite extends Http4sSuite {
  implicit val arbCharset: Arbitrary[Charset] = Arbitrary {
    Gen.oneOf(
      Charset.`UTF-8`,
      Charset.`ISO-8859-1`,
      Charset.fromString("Windows-1251").yolo,
      Charset.fromString("GB2312").yolo,
      Charset.fromString("Shift-JIS").yolo,
      Charset.fromString("Windows-1252").yolo
    )
  }

  test("HTML encoder should return Content-Type text/html with proper charset") {
    Prop.forAll { implicit cs: Charset =>
      val headers = EntityEncoder[IO, Html].headers
      headers.get[`Content-Type`].contains(`Content-Type`(MediaType.text.html, Some(cs)))
    }
  }

  test("HTML encoder should render the body") {
    PropF.forAllF { implicit cs: Charset =>
      val resp = Response[IO](Ok).withEntity(html.test())
      EntityDecoder
        .text[IO]
        .decode(resp, strict = false)
        .value
        .assertEquals(Right("<h1>test html</h1>"))
    }
  }

  test("JS encoder should return Content-Type application/javascript with proper charset") {
    Prop.forAll { implicit cs: Charset =>
      val headers = EntityEncoder[IO, JavaScript].headers
      headers
        .get[`Content-Type`]
        .contains(`Content-Type`(MediaType.application.javascript, Some(cs)))
    }
  }

  test("JS encoder should render the body") {
    PropF.forAllF { implicit cs: Charset =>
      val resp = Response[IO](Ok).withEntity(js.test())
      EntityDecoder.text[IO].decode(resp, strict = false).value.assertEquals(Right(""""test js""""))
    }
  }

  test("Text encoder should return Content-Type text/plain with proper charset") {
    Prop.forAll { implicit cs: Charset =>
      val headers = EntityEncoder[IO, Txt].headers
      headers.get[`Content-Type`].contains(`Content-Type`(MediaType.text.plain, Some(cs)))
    }
  }

  test("Text encoder should render the body") {
    PropF.forAllF { implicit cs: Charset =>
      val resp = Response[IO](Ok).withEntity(txt.test())
      EntityDecoder.text[IO].decode(resp, strict = false).value.assertEquals(Right("""test text"""))
    }
  }

  test("XML encoder should return Content-Type application/xml with proper charset") {
    Prop.forAll { implicit cs: Charset =>
      val headers = EntityEncoder[IO, Xml].headers
      headers.get[`Content-Type`].contains(`Content-Type`(MediaType.application.xml, Some(cs)))
    }
  }

  test("XML encoder should render the body") {
    PropF.forAllF { implicit cs: Charset =>
      val resp = Response[IO](Ok).withEntity(_root_.xml.test())
      EntityDecoder
        .text[IO]
        .decode(resp, strict = false)
        .value
        .assertEquals(Right("<test>test xml</test>"))
    }
  }

}
