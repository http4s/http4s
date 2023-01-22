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

import cats.Applicative
import cats.Functor

object DecodeResult {
  def failed[A](df: DecodeFailure): DecodeResult[A] = Left(df)

  def succeed[A](a: A): DecodeResult[A] = Right(a)

  def success[F[_], A](fa: F[A])(implicit F: Functor[F]): F[DecodeResult[A]] =
    F.map(fa)(Right(_))

  def failure[F[_], A](fe: F[DecodeFailure])(implicit F: Functor[F]): F[DecodeResult[A]] =
    F.map(fe)(Left(_))

  def successT[F[_], A](a: A)(implicit F: Applicative[F]): F[DecodeResult[A]] =
    F.pure(succeed(a))

  def failureT[F[_], A](e: DecodeFailure)(implicit F: Applicative[F]): F[DecodeResult[A]] =
    F.pure(failed(e))
}
