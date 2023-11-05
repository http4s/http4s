/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.dsl

import org.http4s.Method
import org.http4s.Uri
import org.http4s.dsl.impl._

trait RequestDsl extends Methods with Auth {
  import Http4sDsl._

  type Path = Uri.Path
  type Root = Uri.Path.Root.type
  type / = impl./.type
  type MethodConcat = impl.MethodConcat

  val Path: Uri.Path.type
  val Root: Uri.Path.Root.type
  val / : impl./.type
  val :? : impl.:?.type
  val ~ : impl.~.type
  val -> : impl.->.type
  val /: : impl./:.type
  val +& : impl.+&.type

  val IntVar: impl.IntVar.type
  val LongVar: impl.LongVar.type
  val UUIDVar: impl.UUIDVar.type
  val StringVar: impl.StringVar.type

  type QueryParamDecoderMatcher[T] = impl.QueryParamDecoderMatcher[T]
  type QueryParamMatcher[T] = impl.QueryParamMatcher[T]
  type OptionalQueryParamDecoderMatcher[T] = impl.OptionalQueryParamDecoderMatcher[T]
  type QueryParamDecoderMatcherWithDefault[T] = impl.QueryParamDecoderMatcherWithDefault[T]
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

trait RequestDslBinCompat extends RequestDsl {
  val ->> : impl.->>.type = impl.->>
}
