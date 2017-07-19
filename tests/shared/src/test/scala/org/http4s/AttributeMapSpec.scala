package org.http4s

import org.specs2.mutable.Specification

class AttributeMapSpec extends Specification {

  "AttributeMap" should {

    val k1 = AttributeKey[Int]
    val k2 = AttributeKey[String]
    val k3 = AttributeKey[Int]
    val k1Imposter = AttributeKey[Int]

    val m = AttributeMap.empty ++ Seq(k1(1), k2("foo"))

    "Find an item associated with a key" in {
      m.get(k1) must beSome(1)
      m.get(k2) must beSome("foo")
    }

    "Not find a missing item" in {
      m.get(k3) must beNone
    }

    "Not allow imposter keys" in {
      m.get(k1Imposter) must beNone
    }

    // This is a compile test
    "Maintain the correct static type for keys" in {
      sealed case class Foo(stuff: String)

      illTyped("val mismatchedKey = AttributeKey[Foo]; val ii = m(mismatchedKey) + 5")

      val i: Int = m.get(k1).get + 4
      i must_== 5
    }
  }

}
