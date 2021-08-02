/*
 * Copyright 2021 http4s.org
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
package node.serverless

import cats.effect.IO
import cats.effect.kernel.Async
import cats.effect.kernel.Deferred
import cats.effect.unsafe.IORuntime
import cats.syntax.all._
import fs2.io

import scala.scalajs.js
import scala.scalajs.js.JSConverters._

object ServerlessApp {

  def unsafeExportApps(apps: IO[Map[String, IO[HttpApp[IO]]]], exports: String*)(implicit
      runtime: IORuntime): js.Dictionary[js.Function2[IncomingMessage, ServerResponse, Unit]] = {

    val deferredHandlers =
      exports.map(_ -> Deferred.unsafe[IO, (IncomingMessage, ServerResponse) => IO[Unit]]).toMap

    apps
      .flatMap(_.toList.traverse { case (name, app) =>
        app.map(apply[IO]).flatMap(deferredHandlers(name).complete)
      })
      .unsafeRunAndForget()

    deferredHandlers
      .mapValues[js.Function2[IncomingMessage, ServerResponse, Unit]] { handler => (req, res) =>
        handler.get.flatMap(_(req, res)).unsafeRunAndForget()
      }
      .toMap
      .toJSDictionary
  }

  def unsafeExportApp(app: IO[HttpApp[IO]])(implicit
      runtime: IORuntime): js.Function2[IncomingMessage, ServerResponse, Unit] = {
    val handler = Deferred.unsafe[IO, (IncomingMessage, ServerResponse) => IO[Unit]]
    app.map(apply[IO]).flatMap(handler.complete).unsafeRunAndForget()
    (req, res) => handler.get.flatMap(_(req, res)).unsafeRunAndForget()
  }

  def unsafeExportApp(app: HttpApp[IO])(implicit
      runtime: IORuntime): js.Function2[IncomingMessage, ServerResponse, Unit] =
    apply[IO](app).apply(_, _).unsafeRunAndForget()

  def apply[F[_]](app: HttpApp[F])(implicit
      F: Async[F]): (IncomingMessage, ServerResponse) => F[Unit] = { (req, res) =>
    for {
      method <- F.fromEither(Method.fromString(req.method))
      uri <- F.fromEither(Uri.fromString(req.url))
      headers = Headers(req.headers.toList)
      body = io.readReadable[F](F.pure(req))
      request = Request(method, uri, headers = headers, body = body)
      response <- app.run(request)
      _ <- F.delay {
        val headers = response.headers.headers
          .map { case Header.Raw(name, value) =>
            name.toString -> value
          }
          .toMap
          .toJSDictionary
        res.writeHead(response.status.code, response.status.reason, headers)
      }
      _ <- response.body
        .through(io.writeWritable[F](F.pure(res)))
        .compile
        .drain
    } yield ()
  }

}
