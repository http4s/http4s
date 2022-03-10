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

package org.http4s.server.middleware

import cats.effect.IO
import cats.effect.Ref
import com.comcast.ip4s._
import org.http4s.Request.Connection
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.syntax.all._
import org.typelevel.vault.Vault

class ErrorActionSuite extends Http4sSuite {
  private val remote = Ipv4Address.fromBytes(192, 168, 0, 1)

  private def httpRoutes(error: Throwable = new RuntimeException()) =
    HttpRoutes.of[IO] { case GET -> Root / "error" =>
      IO.raiseError(error)
    }

  private val req = Request[IO](
    GET,
    uri"/error",
    attributes = Vault.empty.insert(
      Request.Keys.ConnectionInfo,
      Connection(
        SocketAddress(Ipv4Address.fromBytes(127, 0, 0, 1), port"80"),
        SocketAddress(remote, port"80"),
        false,
      ),
    ),
  )

  private def testApp(app: Ref[IO, Vector[String]] => HttpApp[IO], expected: Vector[String])(
      req: Request[IO]
  ) =
    (for {
      logsRef <- Ref.of[IO, Vector[String]](Vector.empty)
      _ <- app(logsRef).run(req).attempt
      logs <- logsRef.get
    } yield logs).assertEquals(expected)

  private def testHttpRoutes(
      httpRoutes: Ref[IO, Vector[String]] => HttpRoutes[IO],
      expected: Vector[String],
  ) =
    testApp(logsRef => httpRoutes(logsRef).orNotFound, expected)(_)

  test("run the given function when an error happens") {
    testApp(
      logsRef =>
        ErrorAction(
          httpRoutes().orNotFound,
          (_: Request[IO], _) => logsRef.getAndUpdate(_ :+ "Error was handled").void,
        ),
      Vector("Error was handled"),
    )(req)
  }

  test("be created via httpApp constructor") {
    testApp(
      logsRef =>
        ErrorAction.httpApp(
          httpRoutes().orNotFound,
          (_: Request[IO], _) => logsRef.getAndUpdate(_ :+ "Error was handled").void,
        ),
      Vector("Error was handled"),
    )(req)
  }

  test("be created via httRoutes constructor") {
    testHttpRoutes(
      logsRef =>
        ErrorAction.httpRoutes(
          httpRoutes(),
          (_: Request[IO], _) => logsRef.getAndUpdate(_ :+ "Error was handled").void,
        ),
      Vector("Error was handled"),
    )(req)
  }

  test("provide prebaked error message in case of a runtime error") {
    testApp(
      logsRef =>
        ErrorAction.log(
          httpRoutes().orNotFound,
          (_, _) => IO.unit,
          (_, message) => logsRef.getAndUpdate(_ :+ message).void,
        ),
      Vector(s"Error servicing request: GET /error from $remote"),
    )(req)
  }

  test("provide prebaked error message in case of a message failure") {
    testApp(
      logsRef =>
        ErrorAction.log(
          httpRoutes(ParseFailure("some-erroneous-message", "error")).orNotFound,
          (_, message) => logsRef.getAndUpdate(_ :+ message).void,
          (_, _) => IO.unit,
        ),
      Vector(s"Message failure handling request: GET /error from $remote"),
    )(req)
  }

  test("should be created via httpApp.log constructor") {
    testHttpRoutes(
      logsRef =>
        ErrorAction.httpRoutes.log(
          httpRoutes(),
          (_, _) => IO.unit,
          (_, message) => logsRef.getAndUpdate(_ :+ message).void,
        ),
      Vector(s"Error servicing request: GET /error from $remote"),
    )(req)
  }
}
