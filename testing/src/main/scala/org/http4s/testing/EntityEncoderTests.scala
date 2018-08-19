package org.http4s
package testing

import cats.implicits._
import cats.effect._
import cats.effect.implicits._
import cats.effect.laws.util.TestContext
import org.http4s.headers.{`Content-Length`, `Transfer-Encoding`}
import org.scalacheck.{Arbitrary, Prop, Shrink}
import org.typelevel.discipline.Laws

trait EntityEncoderLaws[F[_], A] {
  implicit def effect: Effect[F]
  implicit def encoder: EntityEncoder[F, A]

  def accurateContentLengthIfDefined(a: A) =
    (for {
      entity <- effect.pure(encoder.toEntity(a))
      body <- entity.body.compile.toVector
      bodyLength = body.size.toLong
      contentLength = entity.length
    } yield contentLength.fold(true)(_ === bodyLength)).toIO

  def noContentLengthInStaticHeaders =
    encoder.headers.get(`Content-Length`).isEmpty

  def noTransferEncodingInStaticHeaders =
    encoder.headers.get(`Transfer-Encoding`).isEmpty
}

object EntityEncoderLaws {
  def apply[F[_], A](
      implicit effectF: Effect[F],
      entityEncoderFA: EntityEncoder[F, A]
  ): EntityEncoderLaws[F, A] = new EntityEncoderLaws[F, A] {
    val effect = effectF
    val encoder = entityEncoderFA
  }
}

trait EntityEncoderTests[F[_], A] extends Laws {
  def laws: EntityEncoderLaws[F, A]

  def entityEncoder(
      implicit
      arbitraryA: Arbitrary[A],
      shrinkA: Shrink[A],
      testContext: TestContext): RuleSet = new DefaultRuleSet(
    name = "EntityEncoder",
    parent = None,
    "accurateContentLength" -> Prop.forAll { (a: A) =>
      ioBooleanToProp(laws.accurateContentLengthIfDefined(a))
    },
    "noContentLengthInStaticHeaders" -> laws.noContentLengthInStaticHeaders,
    "noTransferEncodingInStaticHeaders" -> laws.noTransferEncodingInStaticHeaders
  )
}

object EntityEncoderTests {
  def apply[F[_], A](
      implicit effectF: Effect[F],
      entityEncoderFA: EntityEncoder[F, A]
  ): EntityEncoderTests[F, A] = new EntityEncoderTests[F, A] {
    val laws: EntityEncoderLaws[F, A] = EntityEncoderLaws.apply[F, A]
  }
}
