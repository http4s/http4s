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

import cats.effect.{Concurrent, IO}
import cats.implicits._
import org.http4s.implicits._

class RequestSplittingSuite extends Http4sSuite {
  def attack[F[_]](req: Request[F])(implicit F: Concurrent[F]): F[Response[F]] =
    for {
      reqBytes <- Encoder
        .reqToBytes(req)
        .compile
        .to(Array)
      app = HttpApp[F] { req =>
        (req.headers.get("Evil".ci) match {
          case Some(_) => Response[F](Status.InternalServerError)
          case None => Response[F](Status.Ok)
        }).pure[F]
      }
      result <- Parser.Request.parser[F](1024)(reqBytes, F.pure(None))
      (req0, _) = result
      resp <- app(req0)
    } yield resp

  test("Prevent request splitting attacks on URI path") {
    val req = Request[IO](uri = Uri(path = "/ HTTP/1.0\r\nEvil:true\r\nHide-Protocol-Version:"))
    attack(req).intercept[IllegalArgumentException]
  }

  test("Prevent request splitting attacks on URI regname") {
    val req = Request[IO](uri = Uri(
      authority = Uri.Authority(None, Uri.RegName("example.com\r\nEvil:true\r\n")).some,
      path = "/"))
    attack(req).intercept[IllegalArgumentException]
  }
}
