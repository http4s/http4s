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
package client
package dsl

import cats.Applicative
import org.http4s.headers.`Content-Length`

trait Http4sClientDsl[F[_]] {
  implicit def http4sClientSyntaxMethod(method: Method): MethodOps[F] =
    new MethodOps[F](method)

  implicit def http4sHeadersDecoder[T](implicit
      F: Applicative[F],
      decoder: EntityDecoder[F, T],
  ): EntityDecoder[F, (Headers, T)] = {
    val s = decoder.consumes.toList
    EntityDecoder.decodeBy(s.head, s.tail: _*)(resp =>
      decoder.decode(resp, strict = true).map(t => (resp.headers, t))
    )
  }
}

object Http4sClientDsl {
  def apply[F[_]]: Http4sClientDsl[F] = new Http4sClientDsl[F] {}
}

class MethodOps[F[_]](private val method: Method) extends AnyVal {

  /** Make a [[org.http4s.Request]] using this [[Method]] */
  final def apply(uri: Uri, headers: Header.ToRaw*): Request[F] =
    Request(method, uri, headers = Headers(headers: _*))

  /** Make a [[org.http4s.Request]] using this Method */
  final def apply[A](body: A, uri: Uri, headers: Header.ToRaw*)(implicit
      w: EntityEncoder[F, A]
  ): Request[F] = {
    val h = w.headers ++ Headers(headers: _*)
    val entity = w.toEntity(body)
    val newHeaders = entity.length
      .map { l =>
        `Content-Length`.fromLong(l).fold(_ => h, c => h.put(c))
      }
      .getOrElse(h)
    Request(method = method, uri = uri, headers = newHeaders, body = entity.body)
  }
}
