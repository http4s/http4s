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

import cats.effect.kernel.Async
import cats.syntax.all._
import fs2.Stream
import org.scalajs.dom.experimental.ReadableStream
import org.scalajs.dom.experimental.{Headers => DomHeaders}

import scala.scalajs.js.JSConverters._
import scala.scalajs.js.typedarray.Uint8Array
import cats.effect.kernel.Resource

package object dom {

  private[dom] def toDomHeaders(headers: Headers): DomHeaders =
    new DomHeaders(
      headers.headers.view
        .map { case Header.Raw(name, value) =>
          name.toString -> value
        }
        .toMap
        .toJSDictionary)

  private[dom] def fromDomHeaders(headers: DomHeaders): Headers =
    Headers(
      headers.toIterable.map { header =>
        header(0) -> header(1)
      }.toList
    )

  private[dom] def readableStreamToStream[F[_]](rs: ReadableStream[Uint8Array])(implicit
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

  private[dom] def closeReadableStream[F[_], A](rs: ReadableStream[A], exitCase: Resource.ExitCase)(
      implicit F: Async[F]): F[Unit] = F.fromPromise {
    F.delay {
      // Best guess: Firefox internally locks a ReadableStream after it is "drained"
      // This checks if the stream is locked before canceling it to avoid an error
      if (!rs.locked) exitCase match {
        case Resource.ExitCase.Succeeded =>
          rs.cancel(null)
        case Resource.ExitCase.Errored(ex) =>
          rs.cancel(ex.toString())
        case Resource.ExitCase.Canceled =>
          rs.cancel(null)
      }
      else scalajs.js.Promise.resolve[Unit](())
    }
  }.void

}
