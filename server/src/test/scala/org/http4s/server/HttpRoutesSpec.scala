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
package server

import cats.effect._
import cats.syntax.all._
import org.http4s.Uri.uri
import org.http4s.testing.Http4sLegacyMatchersIO

class HttpRoutesSpec extends Http4sSpec with Http4sLegacyMatchersIO {
  val routes1 = HttpRoutes.of[IO] {
    case req if req.pathInfo == "/match" =>
      Response[IO](Status.Ok).withEntity("match").pure[IO]

    case req if req.pathInfo == "/conflict" =>
      Response[IO](Status.Ok).withEntity("routes1conflict").pure[IO]

    case req if req.pathInfo == "/notfound" =>
      Response[IO](Status.NotFound).withEntity("notfound").pure[IO]
  }

  val routes2 = HttpRoutes.of[IO] {
    case req if req.pathInfo == "/routes2" =>
      Response[IO](Status.Ok).withEntity("routes2").pure[IO]

    case req if req.pathInfo == "/conflict" =>
      Response[IO](Status.Ok).withEntity("routes2conflict").pure[IO]
  }

  val aggregate1 = routes1 <+> routes2

  "HttpRoutes" should {
    "Return a valid Response from the first service of an aggregate" in {
      aggregate1.orNotFound(Request[IO](uri = uri("/match"))) must returnBody("match")
    }

    "Return a custom NotFound from the first service of an aggregate" in {
      aggregate1.orNotFound(Request[IO](uri = uri("/notfound"))) must returnBody("notfound")
    }

    "Accept the first matching route in the case of overlapping paths" in {
      aggregate1.orNotFound(Request[IO](uri = uri("/conflict"))) must returnBody("routes1conflict")
    }

    "Fall through the first service that doesn't match to a second matching service" in {
      aggregate1.orNotFound(Request[IO](uri = uri("/routes2"))) must returnBody("routes2")
    }

    "Properly fall through two aggregated service if no path matches" in {
      aggregate1.apply(Request[IO](uri = uri("/wontMatch"))).value must returnValue(
        Option.empty[Response[IO]])
    }
  }
}
