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
import org.scalajs.dom.experimental.ReadableStreamController
import org.scalajs.dom.experimental.RequestInit
import org.scalajs.dom.experimental.HttpMethod
import org.scalajs.dom.experimental.Headers
import org.http4s.Header

import scala.scalajs.js
import scala.scalajs.js.typedarray.{Int8Array, TypedArrayBuffer, Uint8Array}
import scala.scalajs.js.typedarray.TypedArrayBufferOps._
import scala.scalajs.js.JSConverters._
import fs2.Stream
import org.scalajs.dom.experimental.ReadableStream
import cats.effect.std.Dispatcher
import fs2.Pull
import org.scalajs.dom.experimental.Chunk
import org.scalajs.dom.raw.Blob
import org.http4s.Response
import org.http4s.Status

object FetchClient {

  def apply[F[_]: Async]: Client[F] = Client[F] { req: Request[F] =>
    for {
      dispatcher <- Dispatcher[F]
      body <- streamToReadableStream(req.body, dispatcher).toResource
      response <- Async[F].fromPromise {
        Async[F].delay {
          val init = new RequestInit {}

          init.method = req.method.name.asInstanceOf[HttpMethod]

          init.headers = new Headers(req.headers.headers.view.map { case Header.Raw(name, value) =>
            js.Array(name.toString, value)
          }.toJSArray)

          // Hack b/c for some reason ReadableStream is not an accepted type here
          init.body = body.asInstanceOf[Blob]

          Fetch.fetch(req.uri.renderString, init)
        }
      }.toResource
      status <- Async[F].fromEither(Status.fromInt(response.status)).toResource
    } yield Response[F](
      status = status,
      headers = org.http4s.Headers(response.headers.toIterable.map { header =>
        header(0) -> header(1)
      }.toList),
      body = readableStreamToStream(response.body)
    )
  }

  private def streamToReadableStream[F[_]: Async](
      s: Stream[F, Byte],
      dispatcher: Dispatcher[F]): F[ReadableStream[Uint8Array]] =
    Async[F].executionContext.flatMap { implicit ec =>
      Async[F].ref(s).map { ref =>
        val start: js.Function0[Unit] = () => ()

        val pull: js.Function1[ReadableStreamController[Uint8Array], js.Promise[Unit]] = {
          controller =>
            dispatcher.unsafeToFuture {
              for {
                s <- ref.get
                _ <- s.pull.uncons
                  .flatMap {
                    case Some((chunk, tail)) =>
                      val jsChunk = js.Dynamic
                        .literal(
                          value = chunk.toByteBuffer.typedArray().asInstanceOf[Uint8Array],
                          done = false
                        )
                        .asInstanceOf[Chunk[Uint8Array]]
                      Pull.eval(ref.set(tail) *> Async[F].delay(controller.enqueue(jsChunk))).void
                    case None =>
                      Pull.eval(Async[F].delay(controller.close()))
                  }
                  .stream
                  .compile
                  .drain
              } yield ()
            }.toJSPromise
        }

        js.Dynamic.literal(start = start, pull = pull).asInstanceOf[ReadableStream[Uint8Array]]
      }
    }

  private def readableStreamToStream[F[_]: Async](rs: ReadableStream[Uint8Array]): Stream[F, Byte] =
    // TODO bracketCase with r.cancel() ???
    Stream.bracket(rs.getReader().pure)(r => Async[F].delay(r.releaseLock())).flatMap { reader =>
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
