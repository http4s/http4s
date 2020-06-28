/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.dsl

import org.http4s.Method
import org.http4s.dsl.impl._

trait RequestDsl extends Methods with Auth {
  import Http4sDsl._

  type Path = impl.Path
  type Root = impl.Root.type
  type / = impl./
  type MethodConcat = impl.MethodConcat

  val Path: impl.Path.type
  val Root: impl.Root.type
  val / : impl./.type
  val :? : impl.:?.type
  val ~ : impl.~.type
  val -> : impl.->.type
  val /: : impl./:.type
  val +& : impl.+&.type

  val IntVar: impl.IntVar.type
  val LongVar: impl.LongVar.type
  val UUIDVar: impl.UUIDVar.type

  type QueryParamDecoderMatcher[T] = impl.QueryParamDecoderMatcher[T]
  type QueryParamMatcher[T] = impl.QueryParamMatcher[T]
  type OptionalQueryParamDecoderMatcher[T] = impl.OptionalQueryParamDecoderMatcher[T]
  type OptionalMultiQueryParamDecoderMatcher[T] = impl.OptionalMultiQueryParamDecoderMatcher[T]
  type OptionalQueryParamMatcher[T] = impl.OptionalQueryParamMatcher[T]
  type ValidatingQueryParamDecoderMatcher[T] = impl.ValidatingQueryParamDecoderMatcher[T]
  type FlagQueryParamMatcher = impl.FlagQueryParamMatcher
  type OptionalValidatingQueryParamDecoderMatcher[T] =
    impl.OptionalValidatingQueryParamDecoderMatcher[T]

  implicit def http4sMethodSyntax(method: Method): MethodOps =
    new MethodOps(method)

  implicit def http4sMethodConcatSyntax(methods: MethodConcat): MethodConcatOps =
    new MethodConcatOps(methods)
}
