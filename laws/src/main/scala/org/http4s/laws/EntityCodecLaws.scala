/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package laws

import cats.syntax.all._
import cats.effect._
import cats.effect.implicits._
import cats.laws._

trait EntityCodecLaws[F[_], A] extends EntityEncoderLaws[F, A] {
  implicit def F: Effect[F]
  implicit def encoder: EntityEncoder[F, A]
  implicit def decoder: EntityDecoder[F, A]

  def entityCodecRoundTrip(a: A): IsEq[IO[Either[DecodeFailure, A]]] =
    (for {
      entity <- F.delay(encoder.toEntity(a))
      message = Request(body = entity.body, headers = encoder.headers)
      a0 <- decoder.decode(message, strict = true).value
    } yield a0).toIO <-> IO.pure(Right(a))
}

object EntityCodecLaws {
  def apply[F[_], A](implicit
      F0: Effect[F],
      entityEncoderFA: EntityEncoder[F, A],
      entityDecoderFA: EntityDecoder[F, A]): EntityCodecLaws[F, A] =
    new EntityCodecLaws[F, A] {
      val F = F0
      val encoder = entityEncoderFA
      val decoder = entityDecoderFA
    }
}
