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
import fs2.text.utf8Encode
import org.http4s.Status.Ok
import scala.xml.Elem

class ScalaXmlSpec extends Http4sSpec {
  def getBody(body: EntityBody[IO]): Array[Byte] = body.compile.toVector.unsafeRunSync().toArray

  def strBody(body: String): EntityBody[IO] = Stream(body).through(utf8Encode)

  "xml" should {
    val server: Request[IO] => IO[Response[IO]] = { req =>
      req.decode { (elem: Elem) =>
        IO.pure(Response[IO](Ok).withEntity(elem.label))
      }
    }

    "parse the XML" in {
      val resp = server(Request[IO](body = strBody("<html><h1>h1</h1></html>"))).unsafeRunSync()
      resp.status must_== Ok
      getBody(resp.body) must_== "html".getBytes
    }

    "parse XML in parallel" in {
      // https://github.com/http4s/http4s/issues/1209
      val resp = ((0 to 5).toList)
        .parTraverse(_ =>
          server(Request(body = strBody(
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><html><h1>h1</h1></html>"""))))
        .unsafeRunSync()
      resp.forall(_.status must_== Ok)
      resp.forall(x => getBody(x.body) must_== "html".getBytes)
    }

    "return 400 on parse error" in {
      val body = strBody("This is not XML.")
      val tresp = server(Request[IO](body = body))
      tresp.unsafeRunSync().status must_== Status.BadRequest
    }
  }

  "htmlEncoder" should {
    "render html" in {
      val html = <html><body>Hello</body></html>
      writeToString(html) must_== "<html><body>Hello</body></html>"
    }
  }
}
