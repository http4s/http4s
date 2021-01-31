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
import fs2.Stream
import fs2.text.{utf8Decode, utf8Encode}
import org.http4s.Status.Ok
import scala.xml.Elem

class ScalaXmlSuite extends Http4sSuite {
  def getBody(body: EntityBody[IO]): IO[String] =
    body.through(utf8Decode).foldMonoid.compile.lastOrError

  def strBody(body: String): EntityBody[IO] = Stream(body).through(utf8Encode)

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
    val req = Request(body = strBody(
      """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><html><h1>h1</h1></html>"""))
    // https://github.com/http4s/http4s/issues/1209
    ((0 to 5).toList)
      .parTraverse(_ => server(req).flatMap(r => getBody(r.body)))
      .map { bodies =>
        assert(bodies.forall(_ == "html"))
      }
  }

  test("return 400 on parse error") {
    val body = strBody("This is not XML.")
    val tresp = server(Request[IO](body = body))
    tresp.map(_.status).assertEquals(Status.BadRequest)
  }

  test("htmlEncoder renders HTML") {
    val html = <html><body>Hello</body></html>
    assertIO(writeToString(html), "<html><body>Hello</body></html>")
  }
}
