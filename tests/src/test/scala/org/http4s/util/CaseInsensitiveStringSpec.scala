package org.http4s.util

import java.util.Locale

import org.http4s.Http4sSpec
import org.scalacheck.{Prop, Arbitrary, Gen}
import scalaz.scalacheck.ScalazProperties

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

  "slice" should {
    "be consistent with the orignal's slice" in {
      def gen = for {
        s <- Arbitrary.arbitrary[String]
        i <- Arbitrary.arbitrary[Int]
        j <- Arbitrary.arbitrary[Int]
      } yield (s, i, j)
      Prop.forAll(gen) { case (s, i, j) =>
        s.ci.slice(i, j) equals (s.slice(i,j).ci)
      }
    }
  }

  "building from a char" should {
    "be consistent with building a string from a char" in {
      prop { (s: String, c: Char) =>
        (s.ci :+ c) == (s :+ c).ci
      }
    }
  }
}
