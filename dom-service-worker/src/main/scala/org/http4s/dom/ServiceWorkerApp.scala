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
import cats.effect.kernel.Deferred
import cats.effect.unsafe.IORuntime
import cats.syntax.all._
import fs2.Chunk
import fs2.Stream
import org.scalajs.dom.crypto._
import org.scalajs.dom.experimental.Body
import org.scalajs.dom.experimental.ResponseInit
import org.scalajs.dom.experimental.{Request => DomRequest}
import org.scalajs.dom.experimental.{Response => DomResponse}

import scala.scalajs.js

object ServiceWorkerApp {

  def unsafeExportApp(app: IO[HttpApp[IO]])(implicit
      runtime: IORuntime): DomRequest => js.Promise[DomResponse] = {
    val handler = Deferred.unsafe[IO, Kleisli[IO, DomRequest, DomResponse]]
    app.map(apply[IO]).flatMap(handler.complete).unsafeRunAndForget()
    request => handler.get.flatMap(_(request)).unsafeToPromise()
  }

  def unsafeExportApp(app: HttpApp[IO])(implicit
      runtime: IORuntime): DomRequest => js.Promise[DomResponse] =
    apply(app).apply(_).unsafeToPromise()

  def apply[F[_]](app: HttpApp[F])(implicit F: Async[F]): Kleisli[F, DomRequest, DomResponse] =
    Kleisli { req =>
      for {
        method <- F.fromEither(Method.fromString(req.method.asInstanceOf[String]))
        uri <- F.fromEither(Uri.fromString(req.url))
        headers = fromDomHeaders(req.headers)

        body = Stream
          .evalUnChunk(
            F.fromPromise(F.delay(req.asInstanceOf[Body].arrayBuffer())).map(Chunk.jsArrayBuffer))
          .covary[F]
        request = Request(method, uri, headers = headers, body = body)
        response <- app.run(request)
        body <- OptionT(response.body.chunkAll.filter(_.nonEmpty).compile.last).map { chunk =>
          arrayBuffer2BufferSource(chunk.toJSArrayBuffer)
        }.value
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
