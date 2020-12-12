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
import org.http4s.Method.{NoBody, PermitsBody}
import org.http4s.client.impl.{EmptyRequestGenerator, EntityRequestGenerator}

trait Http4sClientDsl[F[_]] {
  import Http4sClientDsl._

  implicit def http4sWithBodySyntax(method: Method with PermitsBody): WithBodyOps[F] =
    new WithBodyOps[F](method)

  implicit def http4sNoBodyOps(method: Method with NoBody): NoBodyOps[F] =
    new NoBodyOps[F](method)

  implicit def http4sHeadersDecoder[T](implicit
      F: Applicative[F],
      decoder: EntityDecoder[F, T]): EntityDecoder[F, (Headers, T)] = {
    val s = decoder.consumes.toList
    EntityDecoder.decodeBy(s.head, s.tail: _*)(resp =>
      decoder.decode(resp, strict = true).map(t => (resp.headers, t)))
  }
}

object Http4sClientDsl {

  /** Syntax classes to generate a request directly from a [[Method]] */
  implicit class WithBodyOps[F[_]](val method: Method with PermitsBody)
      extends AnyVal
      with EntityRequestGenerator[F]
  implicit class NoBodyOps[F[_]](val method: Method with NoBody)
      extends AnyVal
      with EmptyRequestGenerator[F]
}
