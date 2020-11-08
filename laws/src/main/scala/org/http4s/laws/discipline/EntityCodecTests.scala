/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package laws
package discipline

import cats.Eq
import cats.effect._
import cats.laws.discipline._
import org.scalacheck.{Arbitrary, Prop, Shrink}
import cats.effect.std.Dispatcher

trait EntityCodecTests[F[_], A] extends EntityEncoderTests[F, A] {
  def laws: EntityCodecLaws[F, A]

  implicit def eqDecodeFailure: Eq[DecodeFailure] =
    Eq.fromUniversalEquals[DecodeFailure]

  def entityCodec(implicit
      arbitraryA: Arbitrary[A],
      shrinkA: Shrink[A],
      eqA: Eq[A],
      eqFBoolean: Eq[F[Boolean]],
      dispatcher: Dispatcher[F]
  ): RuleSet = {

    implicit def eqF[T](implicit eqT: Eq[T]): Eq[F[T]] =
      Eq.by[F[T], T](f => dispatcher.unsafeRunSync(f))

    new DefaultRuleSet(
      name = "EntityCodec",
      parent = Some(entityEncoder),
      "roundTrip" -> Prop.forAll { (a: A) =>
        laws.entityCodecRoundTrip(a)
      }
    )
  }
}

object EntityCodecTests {
  def apply[F[_], A](implicit
      F: Concurrent[F],
      entityEncoderFA: EntityEncoder[F, A],
      entityDecoderFA: EntityDecoder[F, A]
  ): EntityCodecTests[F, A] =
    new EntityCodecTests[F, A] {
      val laws: EntityCodecLaws[F, A] = EntityCodecLaws[F, A]
    }
}
