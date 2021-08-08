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

import cats.effect.Async
import cats.effect.Resource
import cats.effect.syntax.all._
import cats.syntax.all._
import fs2.Stream
import org.http4s.client.Client
import org.scalajs.dom.crypto._
import org.scalajs.dom.experimental.Fetch
import org.scalajs.dom.experimental.Headers
import org.scalajs.dom.experimental.HttpMethod
import org.scalajs.dom.experimental.ReadableStream
import org.scalajs.dom.experimental.RequestInit

import scala.scalajs.js.typedarray.Uint8Array

object FetchClient {

  def apply[F[_]](implicit F: Async[F]): Client[F] = Client[F] { req: Request[F] =>
    Resource.eval(req.body.chunkAll.filter(_.nonEmpty).compile.last).flatMap { body =>
      Resource
        .makeCase {
          F.fromPromise {
            F.delay {
              val init = new RequestInit {}

              init.method = req.method.name.asInstanceOf[HttpMethod]
              init.headers = new Headers(toDomHeaders(req.headers))
              body.foreach { body =>
                init.body = arrayBuffer2BufferSource(body.toJSArrayBuffer)
              }

              Fetch.fetch(req.uri.renderString, init)
            }
          }
        } {
          case (r, Resource.ExitCase.Succeeded) => F.fromPromise(F.delay(r.body.cancel(null))).void
          case (r, Resource.ExitCase.Errored(ex)) =>
            F.fromPromise(F.delay(r.body.cancel(ex.getMessage()))).void
          case (r, Resource.ExitCase.Canceled) =>
            F.fromPromise(F.delay(r.body.cancel(null))).void
        }
        .evalMap { response =>
          F.fromEither(Status.fromInt(response.status)).map { status =>
            Response[F](
              status = status,
              headers = fromDomHeaders(response.headers),
              body = readableStreamToStream(response.body)
            )
          }
        }

    }
  }

  private def readableStreamToStream[F[_]](rs: ReadableStream[Uint8Array])(implicit
      F: Async[F]): Stream[F, Byte] =
    Stream
      .bracket(F.delay(rs.getReader()))(r => F.delay(r.releaseLock()))
      .flatMap { reader =>
        Stream.unfoldChunkEval(reader) { reader =>
          F.fromPromise(F.delay(reader.read())).map { chunk =>
            if (chunk.done)
              None
            else
              Some((fs2.Chunk.uint8Array(chunk.value), reader))
          }
        }
      }

}
