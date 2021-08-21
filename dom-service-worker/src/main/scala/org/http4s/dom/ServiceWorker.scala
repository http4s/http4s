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
package dom

import cats.data.Kleisli
import cats.data.OptionT
import cats.effect.Async
import cats.effect.IO
import cats.effect.SyncIO
import cats.effect.kernel.Deferred
import cats.effect.std.Supervisor
import cats.effect.unsafe.IORuntime
import cats.syntax.all._
import fs2.Chunk
import fs2.Stream
import org.scalajs.dom.crypto._
import org.scalajs.dom.experimental.Body
import org.scalajs.dom.experimental.ResponseInit
import org.scalajs.dom.experimental.serviceworkers.ExtendableEvent
import org.scalajs.dom.experimental.serviceworkers.FetchEvent
import org.scalajs.dom.experimental.serviceworkers.ServiceWorkerGlobalScope
import org.scalajs.dom.experimental.{Response => DomResponse}
import org.typelevel.vault.Key

object ServiceWorker {

  /** Adds a listener for `FetchEvent`.
    * If the event is not intercepted by `routes` then it is treated as an ordinary request.
    * Additional context can be retrieved via [[FetchEventContext]] including a [[Supervisor]] for running background tasks.
    * @return an action for removing the listener.
    */
  def addFetchEventListener(routes: IO[HttpRoutes[IO]])(implicit
      runtime: IORuntime): SyncIO[SyncIO[Unit]] = for {
    handler <- Deferred.in[SyncIO, IO, Either[Throwable, HttpRoutes[IO]]]
    _ <- SyncIO(routes.attempt.flatMap(handler.complete).unsafeRunAndForget())
    jsHandler = { event =>
      event
        .asInstanceOf[ExtendableEvent]
        .waitUntil(
          Supervisor[IO]
            .use { supervisor =>
              OptionT
                .liftF(handler.get.rethrow)
                .flatMap(routesToListener(_, supervisor, FetchEventContext.IOKey).apply(event))
                .foldF(IO.unit)(res => IO(event.respondWith(res)))
            }
            .unsafeToPromise())
    }: scalajs.js.Function1[FetchEvent, Unit]
    _ <- SyncIO(ServiceWorkerGlobalScope.self.addEventListener("fetch", jsHandler))
  } yield SyncIO(ServiceWorkerGlobalScope.self.removeEventListener("fetch", jsHandler))

  private type FetchEventListener[F[_]] = Kleisli[OptionT[F, *], FetchEvent, DomResponse]

  private def routesToListener[F[_]](
      routes: HttpRoutes[F],
      supervisor: Supervisor[F],
      key: Key[FetchEventContext[F]])(implicit F: Async[F]): FetchEventListener[F] =
    Kleisli { event =>
      val OptionF = Async[OptionT[F, *]]
      val req = event.request
      for {
        method <- OptionF.fromEither(Method.fromString(req.method.asInstanceOf[String]))
        uri <- OptionF.fromEither(Uri.fromString(req.url))
        headers = fromDomHeaders(req.headers)
        body = Stream
          .evalUnChunk(
            F.fromPromise(F.delay(req.asInstanceOf[Body].arrayBuffer())).map(Chunk.jsArrayBuffer))
          .covary[F]
        request = Request(method, uri, headers = headers, body = body)
          .withAttribute(key, FetchEventContext(event, supervisor))
        response <- routes(request)
        body <- OptionT.liftF(OptionT(response.body.chunkAll.filter(_.nonEmpty).compile.last).map {
          chunk =>
            arrayBuffer2BufferSource(chunk.toJSArrayBuffer)
        }.value)
      } yield new DomResponse(
        body.getOrElse(null),
        ResponseInit(
          response.status.code,
          response.status.reason,
          toDomHeaders(response.headers)
        )
      )
    }

}
