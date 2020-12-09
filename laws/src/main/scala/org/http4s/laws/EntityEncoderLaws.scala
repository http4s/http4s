/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package laws

import cats.syntax.all._
import cats.effect._
import cats.laws._
import org.http4s.headers.{`Content-Length`, `Transfer-Encoding`}

trait EntityEncoderLaws[F[_], A] {
  implicit def F: Sync[F]

  implicit def encoder: EntityEncoder[F, A]

  def accurateContentLengthIfDefined(a: A): IsEq[F[Boolean]] =
    (for {
      entity <- F.pure(encoder.toEntity(a))
      body <- entity.body.compile.toVector
      bodyLength = body.size.toLong
      contentLength = entity.length
    } yield contentLength.fold(true)(_ === bodyLength)) <-> F.pure(true)

  def noContentLengthInStaticHeaders: Boolean =
    encoder.headers.get(`Content-Length`).isEmpty

  def noTransferEncodingInStaticHeaders: Boolean =
    encoder.headers.get(`Transfer-Encoding`).isEmpty
}

object EntityEncoderLaws {
  def apply[F[_], A](implicit
      F0: Sync[F],
      entityEncoderFA: EntityEncoder[F, A]
  ): EntityEncoderLaws[F, A] =
    new EntityEncoderLaws[F, A] {
      val F = F0
      val encoder = entityEncoderFA
    }
}
