package org.http4s
package testing

import cats.Eq
import cats.implicits._
import cats.effect._
import cats.effect.implicits._
import cats.effect.laws.util.TestContext
import cats.effect.laws.util.TestInstances._
import cats.laws._
import cats.laws.discipline._
import org.scalacheck.{Arbitrary, Prop, Shrink}

trait EntityCodecLaws[F[_], A] extends EntityEncoderLaws[F, A] {
  implicit def effect: Effect[F]
  implicit def encoder: EntityEncoder[F, A]
  implicit def decoder: EntityDecoder[F, A]

  def entityCodecRoundTrip(a: A): IsEq[IO[Either[DecodeFailure, A]]] =
    (for {
      entity <- effect.delay(encoder.toEntity(a))
      message = Request(body = entity.body, headers = encoder.headers)
      a0 <- decoder.decode(message, strict = true).value
    } yield a0).toIO <-> IO.pure(Right(a))
}

object EntityCodecLaws {
  def apply[F[_], A](
      implicit effectF: Effect[F],
      entityEncoderFA: EntityEncoder[F, A],
      entityDecoderFA: EntityDecoder[F, A]): EntityCodecLaws[F, A] = new EntityCodecLaws[F, A] {
    val effect = effectF
    val encoder = entityEncoderFA
    val decoder = entityDecoderFA
  }
}

trait EntityCodecTests[F[_], A] extends EntityEncoderTests[F, A] {
  def laws: EntityCodecLaws[F, A]

  implicit def eqDecodeFailure: Eq[DecodeFailure] =
    Eq.fromUniversalEquals[DecodeFailure]

  def entityCodec(
      implicit
      arbitraryA: Arbitrary[A],
      shrinkA: Shrink[A],
      eqA: Eq[A],
      testContext: TestContext): RuleSet = new DefaultRuleSet(
    name = "EntityCodec",
    parent = Some(entityEncoder),
    "roundTrip" -> Prop.forAll { (a: A) =>
      laws.entityCodecRoundTrip(a)
    }
  )
}

object EntityCodecTests {
  def apply[F[_], A](
      implicit effectF: Effect[F],
      entityEncoderFA: EntityEncoder[F, A],
      entityDecoderFA: EntityDecoder[F, A]
  ): EntityCodecTests[F, A] = new EntityCodecTests[F, A] {
    val laws: EntityCodecLaws[F, A] = EntityCodecLaws[F, A]
  }
}
