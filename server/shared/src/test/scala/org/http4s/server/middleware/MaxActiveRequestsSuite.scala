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

import cats.data._
import cats.effect._
import cats.implicits._
import org.http4s._
import org.http4s.syntax.all._

class MaxActiveRequestsSuite extends Http4sSuite {
  private val req = Request[IO]()

  private def routes(startedGate: Deferred[IO, Unit], deferred: Deferred[IO, Unit]) =
    Kleisli { (req: Request[IO]) =>
      req match {
        case other if other.method == Method.PUT => OptionT.none[IO, Response[IO]]
        case _ =>
          OptionT.liftF(
            startedGate.complete(()) >> deferred.get >> Response[IO](Status.Ok).pure[IO]
          )
      }
    }

  test("forHttpApp allow a request when allowed") {
    (for {
      deferredStarted <- Deferred[IO, Unit]
      deferredWait <- Deferred[IO, Unit]
      _ <- deferredWait.complete(())
      middle <- MaxActiveRequests.forHttpApp[IO](1)
      httpApp = middle(routes(deferredStarted, deferredWait).orNotFound)
      out <- httpApp.run(req)
    } yield out.status).assertEquals(Status.Ok)
  }

  test("forHttpApp not allow a request if max active") {
    (for {
      deferredStarted <- Deferred[IO, Unit]
      deferredWait <- Deferred[IO, Unit]
      middle <- MaxActiveRequests.forHttpApp[IO](1)
      httpApp = middle(routes(deferredStarted, deferredWait).orNotFound)
      f <- httpApp.run(req).start
      _ <- deferredStarted.get
      out <- httpApp.run(req)
      _ <- f.cancel
    } yield out.status).assertEquals(Status.ServiceUnavailable)

  }
  test("forHttpRoutes allow a request when allowed") {
    (for {
      deferredStarted <- Deferred[IO, Unit]
      deferredWait <- Deferred[IO, Unit]
      _ <- deferredWait.complete(())
      middle <- MaxActiveRequests.forHttpRoutes[IO](1)
      httpApp = middle(routes(deferredStarted, deferredWait)).orNotFound
      out <- httpApp.run(req)
    } yield out.status).assertEquals(Status.Ok)
  }

  test("forHttpRoutes not allow a request if max active") {
    (for {
      deferredStarted <- Deferred[IO, Unit]
      deferredWait <- Deferred[IO, Unit]
      middle <- MaxActiveRequests.forHttpRoutes[IO](1)
      httpApp = middle(routes(deferredStarted, deferredWait)).orNotFound
      f <- httpApp.run(req).start
      _ <- deferredStarted.get
      out <- httpApp.run(req)
      _ <- f.cancel
    } yield out.status).assertEquals(Status.ServiceUnavailable)
  }

  test("forHttpRoutes release resource on None") {
    (for {
      deferredStarted <- Deferred[IO, Unit]
      deferredWait <- Deferred[IO, Unit]
      middle <- MaxActiveRequests.forHttpRoutes[IO](1)
      httpApp = middle(routes(deferredStarted, deferredWait)).orNotFound
      out1 <- httpApp.run(Request(Method.PUT))
      _ <- deferredWait.complete(())
      out2 <- httpApp.run(req)
    } yield (out1.status, out2.status)).assertEquals((Status.NotFound, Status.Ok))
  }
}
