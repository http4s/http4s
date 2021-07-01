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

package org.http4s.fetchclient

import cats.effect.Async
import cats.syntax.all._
import cats.effect.syntax.all._
import org.http4s.client.Client
import org.http4s.Request
import org.scalajs.dom.experimental.Fetch
import org.scalajs.dom.crypto._
import org.scalajs.dom.experimental.RequestInit
import org.scalajs.dom.experimental.HttpMethod
import org.scalajs.dom.experimental.Headers
import org.http4s.Header

import scala.scalajs.js
import scala.scalajs.js.typedarray.{ArrayBuffer, Int8Array, TypedArrayBuffer, Uint8Array}
import scala.scalajs.js.JSConverters._
import fs2.Stream
import org.scalajs.dom.experimental.ReadableStream
import org.http4s.Response
import org.http4s.Status
import cats.SemigroupK
import fs2.Chunk

object FetchClient {

  def apply[F[_]: Async]: Client[F] = Client[F] { req: Request[F] =>
    (for {
      body <- req.body.chunkAll.compile.foldSemigroup(SemigroupK[Chunk].algebra)
      response <- Async[F].fromPromise {
        Async[F].delay {
          val init = new RequestInit {}

          init.method = req.method.name.asInstanceOf[HttpMethod]

          init.headers = new Headers(req.headers.headers.view.map { case Header.Raw(name, value) =>
            js.Array(name.toString, value)
          }.toJSArray)

          body.filter(_.nonEmpty).foreach { body =>
            val ab = new ArrayBuffer(body.size)
            val bb = TypedArrayBuffer.wrap(ab)
            bb.put(body.toByteBuffer)
            init.body = arrayBuffer2BufferSource(ab)
          }

          Fetch.fetch(req.uri.renderString, init)
        }
      }
      status <- Async[F].fromEither(Status.fromInt(response.status))
    } yield Response[F](
      status = status,
      headers = org.http4s.Headers(response.headers.toIterable.map { header =>
        header(0) -> header(1)
      }.toList),
      body = readableStreamToStream(response.body)
    )).toResource
  }

  private def readableStreamToStream[F[_]: Async](rs: ReadableStream[Uint8Array]): Stream[F, Byte] =
    // TODO bracketCase with r.cancel() ???
    Stream.bracket(rs.getReader().pure[F])(r => Async[F].delay(r.releaseLock())).flatMap { reader =>
      Stream.unfoldChunkEval(reader) { reader =>
        Async[F].fromPromise(Async[F].delay(reader.read())).map { chunk =>
          if (chunk.done)
            None
          else
            Some(
              (
                fs2.Chunk.byteBuffer(TypedArrayBuffer.wrap(chunk.value.asInstanceOf[Int8Array])),
                reader))
        }
      }
    }

}
