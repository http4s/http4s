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
import org.http4s.implicits._
import org.typelevel.ci._

import scala.annotation.nowarn

class ResponseSplittingSuite extends Http4sSuite {
  def attack[F[_]](app: HttpApp[F], req: Request[F])(implicit F: Concurrent[F]): F[Response[F]] =
    for {
      resp <- app(req)
      respBytes <- Encoder
        .respToBytes(resp)
        .compile
        .to(Array)
      result <- Parser.Response.parser[F](1024)(respBytes, F.pure(None))
    } yield result._1

  test("Prevent response splitting attacks on status reason phrase") {
    @nowarn("cat=deprecation")
    val app = HttpApp[IO] { req =>
      Response(Status.NoContent.withReason(req.params("reason"))).pure[IO]
    }
    val req = Request[IO](uri = uri"/?reason=%0D%0AEvil:true%0D%0A")
    attack(app, req).map { resp =>
      assertEquals(resp.headers.headers.find(_.name === ci"Evil"), None)
    }
  }

  test("Prevent response splitting attacks on field name") {
    val app = HttpApp[IO] { req =>
      Response(Status.NoContent).putHeaders(req.params("fieldName") -> "oops").pure[IO]
    }
    val req = Request[IO](uri = uri"/?fieldName=Fine:%0D%0AEvil:true%0D%0A")
    attack(app, req).map { resp =>
      assertEquals(resp.headers.headers.find(_.name === ci"Evil"), None)
    }
  }

  test("Prevent response splitting attacks on field value") {
    val app = HttpApp[IO] { req =>
      Response[IO](Status.NoContent)
        .putHeaders("X-Oops" -> req.params("fieldValue"))
        .pure[IO]
    }
    val req = Request[IO](uri = uri"/?fieldValue=%0D%0AEvil:true%0D%0A")
    attack(app, req).map { resp =>
      assertEquals(resp.headers.headers.find(_.name === ci"Evil"), None)
    }
  }
}
