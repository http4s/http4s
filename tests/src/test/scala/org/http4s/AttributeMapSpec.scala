package org.http4s

import org.specs2.mutable.Specification

class AttributeMapSpec extends Specification {

  "AttributeMap" should {

    val k1 = AttributeKey.apply[Int]("int")
    val k2 = AttributeKey.apply[String]("string")
    val k3 = AttributeKey.apply[Int]("missing")
    val k1Clone = AttributeKey.apply[Int]("int")

    val m = AttributeMap.empty ++ Seq(k1(1), k2("foo"))

    "Find an item associated with a key" in {
      m.get(k1) must beSome(1)
      m.get(k2) must beSome("foo")
    }

    "Not find a missing item" in {
      m.get(k3) must beNone
    }

    "Find a value by a key that is equal in value" in {
      m.get(k1Clone) must beSome(1)
    }

    // This is a compile test
    "Maintain the correct static type for keys" in {
      sealed case class Foo(stuff: String)
      val mismatchedKey = AttributeKey.apply[Foo]("mismatched")
//      val ii = m(mismatchedKey) + 5   // FAILS TO COMPILE: Foo doesn't `+` with 5

      val i: Int = m.get(k1).get + 4
      i must_== 5
    }
  }

}
