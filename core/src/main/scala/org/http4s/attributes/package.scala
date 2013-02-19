package org.http4s

package object attributes {

  import shapeless.TypeOperators._
  class ScopableAttributeKey[T](key: AttributeKey[T]) {
    def in[S <: Scope](scope: S): ScopedKey[T, S] = new ScopedKey(tag[S](key), scope)
  }


  implicit def attribute2scoped[T](attributeKey: AttributeKey[T]) = new ScopableAttributeKey(attributeKey)
  implicit def attribute2defaultScope[T, S <: Scope](attributeKey: AttributeKey[T])(implicit scope: S) = attributeKey in scope
  implicit def request2scope(req: RequestPrelude) = RequestScope(req.uuid)
  implicit def app2scope(routes: RouteHandler) = routes.appScope
}
