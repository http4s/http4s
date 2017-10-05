package org.http4s
package testing

import cats.Eq
import cats.implicits._
import cats.laws._
import cats.laws.discipline._
import org.http4s.util.Renderer
import org.scalacheck.{Arbitrary, Prop, Shrink}
import org.typelevel.discipline.Laws

trait HttpCodecLaws[A] {
  implicit def C: HttpCodec[A]

  def httpCodecRoundTrip(a: A): IsEq[ParseResult[A]] =
    C.parse(Renderer.renderString(a)) <-> Right(a)
}

object HttpCodecLaws {
  def apply[A](implicit httpCodecA: HttpCodec[A]): HttpCodecLaws[A] = new HttpCodecLaws[A] {
    val C = httpCodecA
  }
}

trait HttpCodecTests[A] extends Laws {
  def laws: HttpCodecLaws[A]

  def httpCodec(
      implicit
      arbitraryA: Arbitrary[A],
      shrinkA: Shrink[A],
      eqA: Eq[A]): RuleSet = new DefaultRuleSet(
    name = "HTTP codec",
    parent = None,
    "roundTrip" -> Prop.forAll { (a: A) =>
      laws.httpCodecRoundTrip(a)
    }
  )
}

object HttpCodecTests {
  def apply[A: HttpCodec]: HttpCodecTests[A] = new HttpCodecTests[A] {
    val laws: HttpCodecLaws[A] = HttpCodecLaws[A]
  }
}
