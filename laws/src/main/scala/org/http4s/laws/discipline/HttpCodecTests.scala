package org.http4s
package laws
package discipline

import cats.Eq
import cats.implicits._
import cats.laws.discipline._
import org.scalacheck.{Arbitrary, Prop, Shrink}
import org.typelevel.discipline.Laws

trait HttpCodecTests[A] extends Laws {
  def laws: HttpCodecLaws[A]

  def httpCodec(implicit arbitraryA: Arbitrary[A], shrinkA: Shrink[A], eqA: Eq[A]): RuleSet =
    new DefaultRuleSet(
      name = "HTTP codec",
      parent = None,
      "roundTrip" -> Prop.forAll { (a: A) =>
        laws.httpCodecRoundTrip(a)
      }
    )
}

object HttpCodecTests {
  def apply[A: HttpCodec]: HttpCodecTests[A] =
    new HttpCodecTests[A] {
      val laws: HttpCodecLaws[A] = HttpCodecLaws[A]
    }
}
