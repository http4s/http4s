package org.http4s.util

import java.util.Locale

import cats.Show
import cats.kernel.laws.{GroupLaws, OrderLaws}
import org.http4s.Http4sSpec
import org.scalacheck.{Prop, Arbitrary, Gen}

class CaseInsensitiveStringSpec extends Http4sSpec {
  "equals" should {
    "be consistent with equalsIgnoreCase of the values" in {
      prop { s: String =>
        val lc = s.toLowerCase(Locale.ROOT)
        (s.equalsIgnoreCase(lc)) == (s.ci == lc.ci)
      }
    }
  }

  "compareTo" should {
    "be consistent with compareToIgnoreCase" in {
      prop { (a: String, b: String) =>
        a.compareToIgnoreCase(b) == a.ci.compareTo(b.ci)
      }
    }
  }

  "hashCode" should {
    "be consistent with equality" in {
      prop { s: String =>
        val lc = s.toLowerCase(Locale.ROOT)
        (s.ci == lc.ci) ==> (s.ci.## == lc.ci.##)
      }
    }
  }

  "toString" should {
    "return the original as its toString" in {
      prop { s: String => s.ci.toString equals (s)}
    }
  }

  "length" should {
    "be consistent with the orignal's length" in {
      prop { s: String => s.ci.length equals (s.length)}
    }
  }

  "charAt" should {
    "be consistent with the orignal's charAt" in {
      def gen = for {
        s <- Arbitrary.arbitrary[String].suchThat(_.nonEmpty)
        i <- Gen.choose(0, s.length - 1)
      } yield (s, i)
      Prop.forAll(gen) { case (s, i) => s.ci.charAt(i) equals (s.charAt(i)) }
    }
  }

  "subSequence" should {
    "be consistent with the orignal's subSequence" in {
      def gen = for {
        s <- Arbitrary.arbitrary[String].suchThat(_.nonEmpty)
        i <- Gen.choose(0, s.length - 1)
        j <- Gen.choose(i, s.length - 1)
      } yield (s, i, j)
      Prop.forAll(gen) { case (s, i, j) =>
        s.ci.subSequence(i, j) equals (s.subSequence(i, j).toString.ci)
      }
    }
  }

  checkAll("monoid", GroupLaws[CaseInsensitiveString].monoid)
  checkAll("order", OrderLaws[CaseInsensitiveString].order)

  "Show[CaseInsensitiveString]" should {
    "be consistent with toString" in prop { s: CaseInsensitiveString =>
      Show[CaseInsensitiveString].show(s) must_== s.toString
    }
  }
}
