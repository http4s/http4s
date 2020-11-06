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

trait EntityCodecLaws[F[_], A] extends EntityEncoderLaws[F, A] {
  implicit def F: Concurrent[F]
  implicit def encoder: EntityEncoder[F, A]
  implicit def decoder: EntityDecoder[F, A]

  def entityCodecRoundTrip(a: A): IsEq[F[Either[DecodeFailure, A]]] =
    (for {
      entity <- F.pure(encoder.toEntity(a))
      message = Request(body = entity.body, headers = encoder.headers)
      a0 <- decoder.decode(message, strict = true).value
    } yield a0) <-> F.pure(Right(a))
}

object EntityCodecLaws {
  def apply[F[_], A](implicit
      concurrent: Concurrent[F],
      entityEncoderFA: EntityEncoder[F, A],
      entityDecoderFA: EntityDecoder[F, A]): EntityCodecLaws[F, A] =
    new EntityCodecLaws[F, A] {
      val F = concurrent
      val encoder = entityEncoderFA
      val decoder = entityDecoderFA
    }
}
