package org.http4s
package attributes

import org.specs2.mutable
import concurrent.stm._

class AttributesMapSpec extends mutable.Specification {
  object attrKey extends Key[String]
  object notPresentKey extends Key[String]
  object otherKey extends Key[Int]

  val taggedAttr = attrKey in GlobalScope
  val taggedNotPresent = notPresentKey in GlobalScope
  val taggedOther = otherKey in GlobalScope

  isolated

  val attrs = new ValueScope

  val str = "The attribute value"

  taggedAttr.key in attrs := str

  val _ = ScopedAttributes(GlobalScope, Map((taggedAttr.key, str), (otherKey, 4)))

  "An AttributesMap" should {
    "get a stored a key" in {
      attrs.get(taggedAttr.key) must beSome(str)
    }

    "get a stored key with apply" in {
      attrs(taggedAttr.key) must_== "The attribute value"
    }

    "throw key not found when a key is not found" in {
      attrs(taggedNotPresent.key) must throwA[KeyNotFoundException]
    }

    "use += for updating a key" in {
      attrs(taggedOther.key) = 1
      attrs(taggedOther.key) must_== 1
    }
  }
}