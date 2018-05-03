package org.http4s.util

import cats.Show
import cats.implicits._
import cats.kernel.laws.discipline.{MonoidTests, OrderTests}
import org.http4s.Http4sSpec
import org.http4s.PlatformCasing
import org.scalacheck.{Arbitrary, Gen, Prop}

class CaseInsensitiveStringSpec extends Http4sSpec with PlatformCasing {
  "equals" should {
    "be consistent with equalsIgnoreCase of the values" in {
      prop { s: String =>
        val lc = toLowerCase(s)
        s.equalsIgnoreCase(lc) == (s.ci == lc.ci)
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
        val lc = toUpperCase(s)
        (s.ci == lc.ci) ==> (s.ci.## == lc.ci.##)
      }
    }

    "be consistent with equality for ὈΔΥΣΣΕΎΣ and Ὀδυσσεύς" in {
      // ς and Σ are equal in their uppercase forms but not their lowercase
      // forms, so these words are equal ignoring case, but a hashcode based on
      // lowercase forms is not equal. This is the Greek word for Odysseus.
      val s = "ὈΔΥΣΣΕΎΣ"
      val t = "Ὀδυσσεύς"
      (s.ci == t.ci) && (s.ci.## == t.ci.##)
    }

    "be consistent with equality for Straße and STRAẞE" in {
      // ẞ and ß are equal in their lowercase forms but not their uppercase
      // forms, so these words are equal ignoring case, but a hashcode based on
      // uppercase forms is not equal. This is the German word for street,
      // acceptably uppercased for a 2017 orthographical reform.
      val s = "Straße"
      val t = "STRAẞE"
      (s.ci == t.ci) && (s.ci.## == t.ci.##)
    }
  }

  "toString" should {
    "return the original as its toString" in {
      prop { s: String =>
        s.ci.toString.equals(s)
      }
    }
  }

  "length" should {
    "be consistent with the orignal's length" in {
      prop { s: String =>
        s.ci.length.equals(s.length)
      }
    }
  }

  "charAt" should {
    "be consistent with the orignal's charAt" in {
      def gen =
        for {
          s <- Arbitrary.arbitrary[String].suchThat(_.nonEmpty)
          i <- Gen.choose(0, s.length - 1)
        } yield (s, i)
      Prop.forAll(gen) { case (s, i) => s.ci.charAt(i).equals(s.charAt(i)) }
    }
  }

  "subSequence" should {
    "be consistent with the orignal's subSequence" in {
      def gen =
        for {
          s <- Arbitrary.arbitrary[String].suchThat(_.nonEmpty)
          i <- Gen.choose(0, s.length - 1)
          j <- Gen.choose(i, s.length - 1)
        } yield (s, i, j)
      Prop.forAll(gen) {
        case (s, i, j) =>
          s.ci.subSequence(i, j).equals(s.subSequence(i, j).toString.ci)
      }
    }
  }

  checkAll("monoid", MonoidTests[CaseInsensitiveString].monoid)
  checkAll("order", OrderTests[CaseInsensitiveString].order)

  "Show[CaseInsensitiveString]" should {
    "be consistent with toString" in prop { s: CaseInsensitiveString =>
      Show[CaseInsensitiveString].show(s) must_== s.toString
    }
  }
}
