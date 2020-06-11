/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.dsl

import cats.arrow.FunctionK
import org.http4s.Method
import org.http4s.dsl.impl._

trait Http4sDsl2[F[_], G[_]] extends Responses[F, G] with request

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
