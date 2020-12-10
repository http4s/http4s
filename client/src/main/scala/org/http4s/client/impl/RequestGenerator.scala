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

package org.http4s.client.impl

import cats.Applicative
import org.http4s._
import org.http4s.headers.`Content-Length`

sealed trait RequestGenerator extends Any {
  def method: Method
}

trait EmptyRequestGenerator[F[_]] extends Any with RequestGenerator {

  /** Make a [[org.http4s.Request]] using this [[Method]] */
  final def apply(uri: Uri, headers: Header*)(implicit F: Applicative[F]): F[Request[F]] =
    F.pure(Request(method, uri, headers = Headers(headers.toList)))
}

trait EntityRequestGenerator[F[_]] extends Any with EmptyRequestGenerator[F] {

  /** Make a [[org.http4s.Request]] using this Method */
  final def apply[A](body: A, uri: Uri, headers: Header*)(implicit
      F: Applicative[F],
      w: EntityEncoder[F, A]): F[Request[F]] = {
    val h = w.headers ++ Headers(headers.toList)
    val entity = w.toEntity(body)
    val newHeaders = entity.length
      .map { l =>
        `Content-Length`.fromLong(l).fold(_ => h, c => h.put(c))
      }
      .getOrElse(h)
    F.pure(Request(method = method, uri = uri, headers = newHeaders, body = entity.body))
  }
}
