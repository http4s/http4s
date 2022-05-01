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

package org.http4s
package server
package middleware

import cats.FlatMap
import cats.arrow.FunctionK
import cats.data.Kleisli
import cats.data.NonEmptyList
import cats.data.OptionT
import cats.effect.Sync
import cats.syntax.all._
import cats.~>
import fs2._
import org.http4s.headers._
import org.typelevel.ci._

import scala.collection.mutable.ListBuffer

/** Generic middleware to aggregate chunked response into memory.
  *
  * This middleware wraps any function that may produce an Http response.
  * Upon receiving a chunk-encoded response, the composed function will
  * pull and assemble the full response into memory before emitting the
  * whole response. It also removes the "Chunked encoding" headers, and
  * may add a Content-Length header for the whole payload.
  *
  * Reference: "Chunked Transfer Encoding", Section 4.1 of RFC 7230
  *  https://datatracker.ietf.org/doc/html/rfc7230#section-4.1
  */
object ChunkAggregator {
  def apply[F[_]: FlatMap, G[_]: Sync, A](
      f: G ~> F
  )(http: Kleisli[F, A, Response[G]]): Kleisli[F, A, Response[G]] =
    http.flatMapF(response => f(aggregate(response)))

  private[this] def aggregate[G[_]: Sync](r: Response[G]): G[Response[G]] =
    r.body.chunks.compile.toVector // scalafix:ok Http4sFs2Linters.noFs2SyncCompiler; bincompat until 1.0
      .map { vec =>
        val body = Chunk.concat(vec)
        r
          .withBodyStream(Stream.chunk(body))
          .transformHeaders(removeChunkedTransferEncoding(body.size.toLong))
      }

  def httpRoutes[F[_]: Sync](httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    apply(OptionT.liftK[F])(httpRoutes)

  def httpApp[F[_]: Sync](httpApp: HttpApp[F]): HttpApp[F] =
    apply(FunctionK.id[F])(httpApp)

  /* removes the `TransferCoding.chunked` value from the `Transfer-Encoding` header,
   removes the `Content-Length` header, and leaves the other headers unchanged */
  private[this] def removeChunkedTransferEncoding(len: Long)(headers: Headers): Headers = {
    val hh: ListBuffer[Header.ToRaw] = ListBuffer.empty[Header.ToRaw]
    headers.headers.foreach {
      case h @ Header.Raw(ci"Transfer-Encoding", value) =>
        `Transfer-Encoding`.parse(value) match {
          case Right(te) =>
            te.values.filterNot(_ === TransferCoding.chunked) match {
              case v :: vs =>
                hh += `Transfer-Encoding`(NonEmptyList(v, vs))
              case Nil =>
              // do nothing
            }
          case Left(_) =>
            hh += h
        }
      case Header.Raw(ci"Content-Length", _) => // do nothing
      case header => hh += header
    }
    if (len > 0L)
      hh += `Content-Length`.unsafeFromLong(len)
    Headers(hh.toList)
  }
}
