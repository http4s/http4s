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

import cats.effect.IO

sealed abstract class ResponseBuilder[S <: Status, Body, G[x] <: Option[x]](
    val s: S,
    val body: G[Body]) {
  def withEntity[T](b: T)(implicit ev: S <:< Status.Aux[true]): ResponseBuilder[S, T, Some] =
    new ResponseBuilder(s, Some(b)) {}

  def build[F[_]](implicit builder: ResponseBuilder.Build[F, Body, G]): Response[F] = builder(this)
}

object ResponseBuilder {
  type None[A] = None.type

  def apply(status: Status): ResponseBuilder[status.type, Nothing, None] =
    new ResponseBuilder[status.type, Nothing, None](status, None) {}

  sealed trait Build[F[_], Body, G[x] <: Option[x]] {
    def apply(rb: ResponseBuilder[_ <: Status, Body, G]): Response[F]
  }

  object Build {

    implicit def withEntity[F[_], A](implicit eec: EntityEncoder[F, A]): Build[F, A, Some] =
      new Build[F, A, Some] {
        def apply(rb: ResponseBuilder[_ <: Status, A, Some]): Response[F] =
          Response(rb.s).withEntity(rb.body.value)
      }

    implicit def noEntity[F[_]]: Build[F, Nothing, None] = new Build[F, Nothing, None] {
      def apply(rb: ResponseBuilder[_ <: Status, Nothing, None]): Response[F] = Response(rb.s)
    }
  }
}

object ResponseBuilderDemo {
  val ok: Response[IO] = Response.builder(Status.Ok).withEntity("hi").build[IO]
  val noContent = Response.builder(Status.NoContent).build[IO]
  // doesn't compile:
  // ResponseBuilder(Status.NoContent).withEntity("hi")
}
