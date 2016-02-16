package org.http4s

import org.scalacheck.{Prop, Arbitrary}
import org.specs2.ScalaCheck
import org.specs2.matcher.MustMatchers
import org.typelevel.discipline.Laws

import scalaz.\/-

trait HeaderLaws[A <: Header.Parsed] extends Laws { this: Http4sSpec =>
  def checkHeaderLaws(implicit arbA: Arbitrary[A]) =
    checkAll("header laws", headerRuleSet)

  def headerRuleSet(implicit arbA: Arbitrary[A]): RuleSet = {
    new SimpleRuleSet(
      "header",
      """fromString(a.value) == \/-(a)"""" -> prop { a: A =>
        a.key.fromString(a.value) must_== \/-(a)
      },
      """"renderString == "name: value"""" -> prop { a: A =>
        a.renderString must_== s"${a.key.name}: ${a.value}"
      },
      """matchHeader matches""" -> prop { a: A =>
        a.key.matchHeader(a) must beSome(a)
      }
    )
  }
}

