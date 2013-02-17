package org.http4s
package attributes

import org.specs2.mutable
import concurrent.stm._
import shapeless.TypeOperators._

class AttributesMapSpec extends mutable.Specification {
  object attrKey extends Key[String]("attr-key")
  object notPresentKey extends Key[String]("not-present-key")
  object otherKey extends Key[Int]("other-key")

  val taggedAttr = tag[ThisServer.type](attrKey)
  val taggedNotPresent = tag[ThisServer.type](notPresentKey)
  val taggedOther = tag[ThisServer.type](otherKey)

  isolated

  val attrs = new ScopedAttributes[ThisServer.type](ThisServer, TMap.empty)
  attrs += ((taggedAttr,"The attribute value"))
  "An AttributesMap" should {
    "get a stored a key" in {
      attrs.get(taggedAttr) must beSome("The attribute value")
    }

    "get a stored key with apply" in {
      attrs(taggedAttr) must_== "The attribute value"
    }

    "throw key not found when a key is not found" in {

      attrs(taggedNotPresent) must throwA[KeyNotFoundException]
    }

    "use += for updating a key" in {
      attrs(taggedOther) = 1
      attrs(taggedOther) must_== 1
    }
  }
}