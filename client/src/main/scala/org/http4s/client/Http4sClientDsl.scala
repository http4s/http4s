/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
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
      decoder: EntityDecoder[F, T]): EntityDecoder[F, (Headers, T)] = {
    val s = decoder.consumes.toList
    EntityDecoder.decodeBy(s.head, s.tail: _*)(resp =>
      decoder.decode(resp, strict = true).map(t => (resp.headers, t)))
  }
}

class MethodOps[F[_]](private val method: Method) extends AnyVal {

  /** Make a [[org.http4s.Request]] using this [[Method]] */
  final def apply(uri: Uri, headers: Header*)(implicit F: Applicative[F]): F[Request[F]] =
    F.pure(Request(method, uri, headers = Headers(headers.toList)))

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
