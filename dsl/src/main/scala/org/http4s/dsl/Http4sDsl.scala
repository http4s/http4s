/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.dsl

import cats.arrow.FunctionK
import org.http4s.Method
import org.http4s.dsl.impl._

trait Http4sDsl2[F[_], G[_]] extends RequestDsl with Responses[F, G] {
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
}

trait Http4sDsl[F[_]] extends Http4sDsl2[F, F] {
  val liftG: FunctionK[F, F] = FunctionK.id[F]
}

object Http4sDsl {
  def apply[F[_]]: Http4sDsl[F] = new Http4sDsl[F] {}

  final class MethodOps(val method: Method) extends AnyVal {
    def |(another: Method) = new MethodConcat(Set(method, another))
  }

  final class MethodConcatOps(val methods: MethodConcat) extends AnyVal {
    def |(another: Method) = new MethodConcat(methods.methods + another)
  }
}
