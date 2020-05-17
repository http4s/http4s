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
import org.typelevel.discipline.Laws

trait EntityEncoderTests[F[_], A] extends Laws {
  def laws: EntityEncoderLaws[F, A]

  def entityEncoder(implicit
      arbitraryA: Arbitrary[A],
      shrinkA: Shrink[A],
      eqFBoolean: Eq[F[Boolean]]
  ): RuleSet =
    new DefaultRuleSet(
      name = "EntityEncoder",
      parent = None,
      "accurateContentLength" -> Prop.forAll { (a: A) =>
        laws.accurateContentLengthIfDefined(a)
      },
      "noContentLengthInStaticHeaders" -> laws.noContentLengthInStaticHeaders,
      "noTransferEncodingInStaticHeaders" -> laws.noTransferEncodingInStaticHeaders
    )
}

object EntityEncoderTests {
  def apply[F[_], A](implicit
      effectF: Effect[F],
      entityEncoderFA: EntityEncoder[F, A]
  ): EntityEncoderTests[F, A] =
    new EntityEncoderTests[F, A] {
      val laws: EntityEncoderLaws[F, A] = EntityEncoderLaws.apply[F, A]
    }
}
