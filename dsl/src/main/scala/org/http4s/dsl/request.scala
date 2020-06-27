/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.dsl

import org.http4s.Method
import org.http4s.dsl.impl._

trait request extends Methods with Statuses with Auth {
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

  /**
    * Alias for `->`.
    *
    * Note: Due to infix operation precedence, `→` has a lower priority than `/`. So you have to use parentheses in
    * pattern matching when using this operator.
    *
    * For example:
    * {{{
    *   (request.method, Path(request.path)) match {
    *     case Method.GET → (Root / "test.json") => ...
    * }}}
    */
  val → : impl.->.type = impl.->

  val IntVar: impl.IntVar.type = impl.IntVar
  val LongVar: impl.LongVar.type = impl.LongVar
  val UUIDVar: impl.UUIDVar.type = impl.UUIDVar

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

object request extends request
