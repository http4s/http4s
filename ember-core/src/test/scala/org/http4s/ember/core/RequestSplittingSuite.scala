/*
 * Copyright 2019 http4s.org
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
package ember.core

import cats.effect.Concurrent
import cats.effect.IO
import cats.implicits._
import org.typelevel.ci._

class RequestSplittingSuite extends Http4sSuite {
  def attack[F[_]](req: Request[F])(implicit F: Concurrent[F]): F[Response[F]] =
    for {
      reqBytes <- Encoder
        .reqToBytes(req)
        .compile
        .to(Array)
      _ = println(new String(reqBytes))
      app = HttpApp[F] { req =>
        (req.headers.get(ci"Evil") match {
          case Some(_) => Response[F](Status.InternalServerError)
          case None => Response[F](Status.Ok)
        }).pure[F]
      }
      result <- Parser.Request.parser[F](1024)(reqBytes, F.pure(None))
      (req0, _) = result
      resp <- app(req0)
    } yield resp

  test("Prevent request splitting attacks on URI path") {
    val req = Request[IO](uri =
      Uri(path =
        Uri.Path.Root / Uri.Path.Segment.encoded(" HTTP/1.0\r\nEvil:true\r\nHide-Protocol-Version:")
      )
    )
    attack(req).intercept[IllegalArgumentException]
  }

  test("Prevent request splitting attacks on URI regname") {
    val req = Request[IO](uri =
      Uri(
        authority = Uri.Authority(None, Uri.RegName("example.com\r\nEvil:true\r\n")).some,
        path = Uri.Path.Root,
      )
    )
    attack(req).map(_.status).assertEquals(Status.Ok)
  }

  test("Prevent request splitting attacks on field name") {
    val req = Request[IO]().putHeaders(Header.Raw(ci"Fine:\r\nEvil:true\r\n", "oops"))
    attack(req).map(_.status).assertEquals(Status.Ok)
  }

  test("Prevent request splitting attacks on field value") {
    val req = Request[IO]().putHeaders(Header.Raw(ci"X-Carrier", "\r\nEvil:true\r\n"))
    attack(req).map(_.status).assertEquals(Status.Ok)
  }
}
