/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
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
