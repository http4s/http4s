/*
 * Copyright 2015 http4s.org
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

package org.http4s.circe.middleware

import cats.data.Kleisli
import cats.effect.IO
import org.http4s._
import org.http4s.testing.SilenceOutputStream

class JsonDebugErrorHandlerSpec extends Http4sSpec with SilenceOutputStream {

  "JsonDebugErrorHandler" should {
    "handle an unknown error" in {
      val service: Kleisli[IO, Request[IO], Response[IO]] =
        Kleisli { (_: Request[IO]) =>
          IO.raiseError[Response[IO]](new Throwable("Boo!"))
        }
      val req: Request[IO] = Request[IO](Method.GET)

      JsonDebugErrorHandler(service)
        .run(req)
        .attempt
        .unsafeRunSync() must beRight
    }
    "handle an message failure" in {
      val service: Kleisli[IO, Request[IO], Response[IO]] =
        Kleisli { (_: Request[IO]) =>
          IO.raiseError[Response[IO]](MalformedMessageBodyFailure("Boo!"))
        }
      val req: Request[IO] = Request[IO](Method.GET)

      JsonDebugErrorHandler(service)
        .run(req)
        .attempt
        .unsafeRunSync() must beRight
    }
  }
}
