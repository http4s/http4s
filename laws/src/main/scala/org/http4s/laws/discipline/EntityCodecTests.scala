package org.http4s
package laws
package discipline

import cats.Eq
import cats.implicits._
import cats.effect._
import cats.effect.laws.util.TestContext
import cats.effect.laws.util.TestInstances._
import cats.laws.discipline._
import org.scalacheck.{Arbitrary, Prop, Shrink}

trait EntityCodecTests[F[_], A] extends EntityEncoderTests[F, A] {
  def laws: EntityCodecLaws[F, A]

  implicit def eqDecodeFailure: Eq[DecodeFailure] =
    Eq.fromUniversalEquals[DecodeFailure]

  def entityCodec(implicit
      arbitraryA: Arbitrary[A],
      shrinkA: Shrink[A],
      eqA: Eq[A],
      eqFBoolean: Eq[F[Boolean]],
      testContext: TestContext): RuleSet =
    new DefaultRuleSet(
      name = "EntityCodec",
      parent = Some(entityEncoder),
      "roundTrip" -> Prop.forAll { (a: A) =>
        laws.entityCodecRoundTrip(a)
      }
    )
}

object EntityCodecTests {
  def apply[F[_], A](implicit
      effectF: Effect[F],
      entityEncoderFA: EntityEncoder[F, A],
      entityDecoderFA: EntityDecoder[F, A]
  ): EntityCodecTests[F, A] =
    new EntityCodecTests[F, A] {
      val laws: EntityCodecLaws[F, A] = EntityCodecLaws[F, A]
    }
}
