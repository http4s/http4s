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

import cats.arrow.FunctionK
import org.http4s.Method
import org.http4s.Uri
import org.http4s.dsl.impl.*

trait Http4sDsl2[F[_], G[_]] extends RequestDsl with Statuses with Responses[F, G] {
  val Path: Uri.Path.type = Uri.Path
  val Root: Uri.Path.Root.type = Uri.Path.Root
  val / : impl./.type = impl./
  val :? : impl.:?.type = impl.:?
  val ~ : impl.~.type = impl.~
  val -> : impl.->.type = impl.->
  val /: : impl./:.type = impl./:
  val +& : impl.+&.type = impl.+&

  /** Alias for `->`.
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

  lazy val PathVar: impl.PathVar.type = impl.PathVar

  val IntVar: impl.IntVar.type = impl.IntVar
  val LongVar: impl.LongVar.type = impl.LongVar
  val UUIDVar: impl.UUIDVar.type = impl.UUIDVar
}

trait Http4sDsl[F[_]] extends Http4sDsl2[F, F] {
  val liftG: FunctionK[F, F] = FunctionK.id[F]
}

object Http4sDsl {
  // Does not return Http4sDslBinCompat for bincompat reasons. ¯\_(ツ)_/¯
  def apply[F[_]]: Http4sDsl[F] with RequestDslBinCompat = new Http4sDslBinCompat[F] {}

  final class MethodOps(val method: Method) extends AnyVal {
    def |(another: Method) = new MethodConcat(Set(method, another))
  }

  final class MethodConcatOps(val methods: MethodConcat) extends AnyVal {
    def |(another: Method) = new MethodConcat(methods.methods + another)
  }
}

trait Http4sDslBinCompat[F[_]] extends Http4sDsl[F] with RequestDslBinCompat
