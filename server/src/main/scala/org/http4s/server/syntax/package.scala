/*
 * Copyright 2014 http4s.org
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

package org.http4s
package server

import cats.Semigroup
import cats.data.Kleisli
import cats.syntax.all._

package object syntax {
  @deprecated(
    "Import `cats.implicits._` and use `kleisli1 <+> kleisli2` instead. Ensure -Ypartial-unification is enabled.",
    "0.16")
  final implicit class ServiceOps[F[_], A, B](val kleisli: Kleisli[F, A, B])(implicit
      B: Semigroup[F[B]]) {
    def ||(fallback: Kleisli[F, A, B]) = orElse(fallback)
    def orElse(fallback: Kleisli[F, A, B]) = kleisli |+| fallback
  }
}
