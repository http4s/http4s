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
package scalaxml

import cats.effect._
import cats.syntax.all._
import fs2.Chunk
import fs2.Stream
import fs2.text.decodeWithCharset
import fs2.text.utf8
import org.http4s.Status.Ok
import org.http4s.headers.`Content-Type`
import org.http4s.laws.discipline.arbitrary._
import org.scalacheck.Prop._
import org.typelevel.ci._

import java.nio.charset.StandardCharsets
import scala.xml.Elem

class ScalaXmlSuite extends Http4sSuite {
  def getBody(body: EntityBody[IO]): IO[String] =
    body.through(utf8.decode).foldMonoid.compile.lastOrError

  def strBody(body: String): EntityBody[IO] = Stream(body).through(utf8.encode)

  val server: Request[IO] => IO[Response[IO]] = { req =>
    req.decode { (elem: Elem) =>
      IO.pure(Response[IO](Ok).withEntity(elem.label))
    }
  }

  test("xml should parse the XML") {
    server(Request[IO](body = strBody("<html><h1>h1</h1></html>")))
      .flatMap(r => getBody(r.body))
      .assertEquals("html")
  }

  test("parse XML in parallel") {
    val req = Request(body =
      strBody("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><html><h1>h1</h1></html>""")
    )
    // https://github.com/http4s/http4s/issues/1209
    (0 to 5).toList
      .parTraverse(_ => server(req).flatMap(r => getBody(r.body)))
      .map { bodies =>
        bodies.foreach { body =>
          assertEquals(body, "html")
        }
      }
  }

  test("return 400 on parse error") {
    val body = strBody("This is not XML.")
    val tresp = server(Request[IO](body = body))
    tresp.map(_.status).assertEquals(Status.BadRequest)
  }

  test("htmlEncoder renders HTML") {
    val html = <html><body>Hello</body></html>
    implicit val cs: Charset = Charset.`UTF-8`
    assertIO(
      writeToString(html),
      """<?xml version='1.0' encoding='UTF-8'?>
        |<html><body>Hello</body></html>""".stripMargin,
    )
  }

  test("encode to UTF-8") {
    val hello = <hello name="Günther"/>
    assertIO(
      xmlEncoder[IO](Charset.`UTF-8`)
        .toEntity(hello)
        .body
        .through(fs2.text.utf8.decode)
        .compile
        .string,
      """<?xml version='1.0' encoding='UTF-8'?>
        |<hello name="Günther"/>""".stripMargin,
    )
  }

  test("encode to UTF-16") {
    val hello = <hello name="Günther"/>
    assertIO(
      xmlEncoder[IO](Charset.`UTF-16`)
        .toEntity(hello)
        .body
        .through(decodeWithCharset(StandardCharsets.UTF_16))
        .compile
        .string,
      """<?xml version='1.0' encoding='UTF-16'?>
        |<hello name="Günther"/>""".stripMargin,
    )
  }

  test("encode to ISO-8859-1") {
    val hello = <hello name="Günther"/>
    assertIO(
      xmlEncoder[IO](Charset.`ISO-8859-1`)
        .toEntity(hello)
        .body
        .through(decodeWithCharset(StandardCharsets.ISO_8859_1))
        .compile
        .string,
      """<?xml version='1.0' encoding='ISO-8859-1'?>
        |<hello name="Günther"/>""".stripMargin,
    )
  }

  property("encoder sets charset of Content-Type") {
    forAll { (cs: Charset) =>
      assertEquals(xmlEncoder[IO](cs).headers.get[`Content-Type`].flatMap(_.charset), Some(cs))
    }
  }

  private def encodingTest(bytes: Chunk[Byte], contentType: String, name: String) = {
    val body = Stream.chunk(bytes)
    val msg = Request[IO](Method.POST, headers = Headers(Header.Raw(ci"Content-Type", contentType)))
      .withBodyStream(body)
    msg.as[Elem].map(_ \\ "hello" \@ "name").assertEquals(name)
  }

  test("parse UTF-8 charset with explicit encoding") {
    // https://datatracker.ietf.org/doc/html/rfc7303#section-8.1
    encodingTest(
      Chunk.array(
        """<?xml version="1.0" encoding="utf-8"?><hello name="Günther"/>""".getBytes(
          StandardCharsets.UTF_8
        )
      ),
      "application/xml; charset=utf-8",
      "Günther",
    )
  }

  test("parse UTF-8 charset with no encoding") {
    // https://datatracker.ietf.org/doc/html/rfc7303#section-8.1
    encodingTest(
      Chunk.array(
        """<?xml version="1.0"?><hello name="Günther"/>""".getBytes(StandardCharsets.UTF_8)
      ),
      "application/xml; charset=utf-8",
      "Günther",
    )
  }

  test("parse UTF-16 charset with explicit encoding") {
    // https://datatracker.ietf.org/doc/html/rfc7303#section-8.2
    encodingTest(
      Chunk.array(
        """<?xml version="1.0" encoding="utf-16"?><hello name="Günther"/>""".getBytes(
          StandardCharsets.UTF_16
        )
      ),
      "application/xml; charset=utf-16",
      "Günther",
    )
  }

  test("parse UTF-16 charset with no encoding") {
    // https://datatracker.ietf.org/doc/html/rfc7303#section-8.2
    encodingTest(
      Chunk.array(
        """<?xml version="1.0"?><hello name="Günther"/>""".getBytes(StandardCharsets.UTF_16)
      ),
      "application/xml; charset=utf-16",
      "Günther",
    )
  }

  test("parse omitted charset and 8-Bit MIME Entity") {
    // https://datatracker.ietf.org/doc/html/rfc7303#section-8.3
    encodingTest(
      Chunk.array(
        """<?xml version="1.0" encoding="iso-8859-1"?><hello name="Günther"/>""".getBytes(
          StandardCharsets.ISO_8859_1
        )
      ),
      "application/xml",
      "Günther",
    )
  }

  test("parse omitted charset and 16-Bit MIME Entity") {
    // https://datatracker.ietf.org/doc/html/rfc7303#section-8.4
    encodingTest(
      Chunk.array(
        """<?xml version="1.0" encoding="utf-16"?><hello name="Günther"/>""".getBytes(
          StandardCharsets.UTF_16
        )
      ),
      "application/xml",
      "Günther",
    )
  }

  test("parse omitted charset, no internal encoding declaration") {
    // https://datatracker.ietf.org/doc/html/rfc7303#section-8.5
    encodingTest(
      Chunk.array(
        """<?xml version="1.0"?><hello name="Günther"/>""".getBytes(StandardCharsets.UTF_8)
      ),
      "application/xml",
      "Günther",
    )
  }

  test("parse utf-16be charset") {
    // https://datatracker.ietf.org/doc/html/rfc7303#section-8.6
    encodingTest(
      Chunk.array(
        """<?xml version="1.0"?><hello name="Günther"/>""".getBytes(StandardCharsets.UTF_16BE)
      ),
      "application/xml; charset=utf-16be",
      "Günther",
    )
  }

  test("parse non-utf charset") {
    // https://datatracker.ietf.org/doc/html/rfc7303#section-8.7
    encodingTest(
      Chunk.array(
        """<?xml version="1.0" encoding="iso-2022-kr"?><hello name="문재인"/>""".getBytes(
          "iso-2022-kr"
        )
      ),
      "application/xml; charset=iso-2022kr",
      "문재인",
    )
  }

  test("parse conflicting charset and internal encoding") {
    // https://datatracker.ietf.org/doc/html/rfc7303#section-8.8
    encodingTest(
      Chunk.array(
        """<?xml version="1.0" encoding="utf-8"?><hello name="Günther"/>""".getBytes(
          StandardCharsets.ISO_8859_1
        )
      ),
      "application/xml; charset=iso-8859-1",
      "Günther",
    )
  }
}
