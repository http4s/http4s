package org.http4s.dsl

import org.http4s.{Http4s, Method}
import org.http4s.dsl.impl._

trait Http4sDsl extends Http4s with Methods with Statuses with Auth with Responses {
  import Http4sDsl._

  type Path = impl.Path
  type Root = impl.Root.type
  type / = impl./
  type MethodConcat = impl.MethodConcat

  val Path: impl.Path.type = impl.Path
  val Root: impl.Root.type = impl.Root
  val / : impl./.type = impl./
  val :? : impl.:?.type = impl.:?
  val ~ : impl.~.type = impl.~
  val -> : impl.->.type = impl.->
  val /: : impl./:.type = impl./:
  val +& : impl.+&.type = impl.+&

  val IntVar: impl.IntVar.type = impl.IntVar
  val LongVar: impl.LongVar.type = impl.LongVar
  val UUIDVar: impl.UUIDVar.type = impl.UUIDVar

  type QueryParamDecoderMatcher[T] = impl.QueryParamDecoderMatcher[T]
  type QueryParamMatcher[T] = impl.QueryParamMatcher[T]
  type OptionalQueryParamDecoderMatcher[T] = impl.OptionalQueryParamDecoderMatcher[T]
  type OptionalMultiQueryParamDecoderMatcher[T] = impl.OptionalMultiQueryParamDecoderMatcher[T]
  type OptionalQueryParamMatcher[T] = impl.OptionalQueryParamMatcher[T]
  type ValidatingQueryParamDecoderMatcher[T] = impl.ValidatingQueryParamDecoderMatcher[T]
  type OptionalValidatingQueryParamDecoderMatcher[T] =
    impl.OptionalValidatingQueryParamDecoderMatcher[T]

  implicit def http4sMethodSyntax(method: Method): MethodOps =
    new MethodOps(method)

  implicit def http4sMethodConcatSyntax(methods: MethodConcat): MethodConcatOps =
    new MethodConcatOps(methods)

}

object Http4sDsl extends Http4sDsl {

  final class MethodOps(val method: Method) extends AnyVal {
    def |(another: Method) = new MethodConcat(Set(method, another))
  }

  final class MethodConcatOps(val methods: MethodConcat) extends AnyVal {
    def |(another: Method) = new MethodConcat(methods.methods + another)
  }
}
