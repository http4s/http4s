package org.http4s

package object attributes {

  import shapeless.TypeOperators._
  class ScopableAttributeKey[T](key: AttributeKey[T]) {
    def in[S <: Scope](scope: S): AttributeKey[T] @@ S = tag[S](key)

  }
  implicit def attribute2scoped[T](attributeKey: AttributeKey[T]) = new ScopableAttributeKey(attributeKey)
}
