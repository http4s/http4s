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

package org.http4s

import cats.Functor
import cats.data.Kleisli

object AuthedRequest {
  def apply[F[_]: Functor, Body, T](
      getUser: Request[Body] => F[T]): Kleisli[F, Request[Body], AuthedRequest[Body, T]] =
    ContextRequest[F, T, Body](getUser)

  def apply[Body, T](context: T, req: Request[Body]): AuthedRequest[Body, T] =
    ContextRequest[Body, T](context, req)
}
