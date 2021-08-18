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
import org.http4s.ResponseBuilder.None
import org.http4s.Status.Ok

import scala.annotation.{implicitNotFound, unused}
import scala.util.NotGiven

sealed abstract class ResponseBuilder[S <: Status, Body, G[x] <: Option[x]](
    val s: S,
    val body: G[Body]) {
  def withEntity[T](b: T)(implicit
      @implicitNotFound(s"This response status does not allow a body") @unused ev: S <:< Status.Aux[
        _,
        true]): ResponseBuilder[S, T, Some] =
    new ResponseBuilder(s, Some(b)) {}

  // TODO: implement
  def withEspressoShots(@unused n: Int)(implicit
  @implicitNotFound("teapots cannot brew espresso") @unused ev: NotGiven[S <:< Status.Aux[418, _]])
      : ResponseBuilder[S, Body, G] =
    this

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
  val noContent: Response[IO] = Response.builder(Status.NoContent).build[IO]

  val `😳` : ResponseBuilder[Ok.type, Nothing, None] =
    Response.builder(Status.Ok).withEspressoShots(6)

  //  Response.builder(Status.NoContent).withEntity("hi")

  //  [error] http4s/core/src/main/scala-2.13+/org.http4s/ResponseBuilder.scala:62:47: This response status does not allow a body
  //  [error]   Response.builder(Status.NoContent).withEntity("hi")
  //  [error]                                               ^

  //  Response.builder(Status.ImATeapot).withEspressoShots(1)

  // dotc:
  //  [error] -- Error: http4s/core/src/main/scala-2.13+/org.http4s/ResponseBuilder.scala:74:57
  //  [error] 74 |  Response.builder(Status.ImATeapot).withEspressoShots(1)
  //  [error]    |                                                         ^
  //  [error]    |                                            teapots cannot brew espresso

  // scalac:
  //  [error] http4s/core/src/main/scala-2.13+/org.http4s/ResponseBuilder.scala:85:55: ambiguous implicit values:
  //  [error]  both method instance1 in object NotGiven of type [A]scala.util.NotGiven[A]
  //  [error]  and method instance2 in object NotGiven of type [A](implicit ev: A): scala.util.NotGiven[A]
  //  [error]  match expected type scala.util.NotGiven[org.http4s.Status.ImATeapot.type <:< org.http4s.Status.Aux[418, _]]
  //  [error]   Response.builder(Status.ImATeapot).withEspressoShots(1)
  //  [error]                                                       ^
}
