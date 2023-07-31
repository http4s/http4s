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
package middleware

import cats.Monad
import cats.data.Kleisli
import cats.effect._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.~>
import org.http4s.Http
import org.typelevel.ci._
import org.typelevel.vault.Key

object HttpMethodOverrider {

  /** HttpMethodOverrider middleware config options.
    */
  class HttpMethodOverriderConfig[F[_], G[_]](
      val overrideStrategy: OverrideStrategy[F, G],
      val overridableMethods: Set[Method],
  ) {
    type Self = HttpMethodOverriderConfig[F, G]

    private def copy(
        overrideStrategy: OverrideStrategy[F, G] = overrideStrategy,
        overridableMethods: Set[Method] = overridableMethods,
    ): Self =
      new HttpMethodOverriderConfig[F, G](overrideStrategy, overridableMethods)

    def withOverrideStrategy(overrideStrategy: OverrideStrategy[F, G]): Self =
      copy(overrideStrategy = overrideStrategy)

    def withOverridableMethods(overridableMethods: Set[Method]): Self =
      copy(overridableMethods = overridableMethods)
  }

  object HttpMethodOverriderConfig {
    def apply[F[_], G[_]](
        overrideStrategy: OverrideStrategy[F, G],
        overridableMethods: Set[Method],
    ): HttpMethodOverriderConfig[F, G] =
      new HttpMethodOverriderConfig[F, G](overrideStrategy, overridableMethods)
  }

  sealed trait OverrideStrategy[F[_], G[_]]
  final case class HeaderOverrideStrategy[F[_], G[_]](headerName: CIString)
      extends OverrideStrategy[F, G]
  final case class QueryOverrideStrategy[F[_], G[_]](paramName: String)
      extends OverrideStrategy[F, G]
  final case class FormOverrideStrategy[F[_], G[_]](
      fieldName: String,
      naturalTransformation: G ~> F,
  ) extends OverrideStrategy[F, G]

  def defaultConfig[F[_], G[_]]: HttpMethodOverriderConfig[F, G] =
    HttpMethodOverriderConfig[F, G](
      HeaderOverrideStrategy(ci"X-HTTP-Method-Override"),
      Set(Method.POST),
    )

  val overriddenMethodAttrKey: Key[Method] = Key.newKey[SyncIO, Method].unsafeRunSync()

  /** Simple middleware for HTTP Method Override.
    *
    * This middleware lets you use  HTTP verbs such as PUT or DELETE in places where the client
    * doesn't support it. Camouflage your request with another HTTP verb(usually POST) and sneak
    * the desired one using a custom header or request parameter. The middleware will '''override'''
    * the original verb with the new one for you, allowing the request to be dispatched properly.
    *
    * @param http [[Http]] to transform
    * @param config http method overrider config
    */
  def apply[F[_], G[_]](http: Http[F, G], config: HttpMethodOverriderConfig[F, G])(implicit
      F: Monad[F],
      S: Concurrent[G],
  ): Http[F, G] = {
    val parseMethod = (m: String) => Method.fromString(m.toUpperCase)

    def processRequestWithMethod(
        req: Request[G],
        parseResult: ParseResult[Method],
    ): F[Response[G]] =
      parseResult match {
        case Left(_) => F.pure(Response[G](Status.BadRequest))
        case Right(om) => http(updateRequestWithMethod(req, om)).map(updateVaryHeader)
      }

    def updateVaryHeader(resp: Response[G]): Response[G] = {
      val varyHeaderName = ci"Vary"
      config.overrideStrategy match {
        case HeaderOverrideStrategy(headerName) =>
          val updatedVaryHeader =
            resp.headers
              .get(varyHeaderName)
              .map(_.head)
              .map((h: Header.Raw) => Header.Raw(h.name, s"${h.value}, ${headerName.toString}"))
              .getOrElse(Header.Raw(varyHeaderName, headerName.toString))

          resp.withHeaders(resp.headers.put(updatedVaryHeader))
        case _ => resp
      }
    }

    def updateRequestWithMethod(req: Request[G], om: Method): Request[G] = {
      val attrs = req.attributes.insert(overriddenMethodAttrKey, req.method)
      req.withAttributes(attrs).withMethod(om)
    }

    def getUnsafeOverrideMethod(req: Request[G]): F[Option[String]] =
      config.overrideStrategy match {
        case HeaderOverrideStrategy(headerName) =>
          F.pure(req.headers.get(headerName).map(_.head.value))
        case QueryOverrideStrategy(parameter) => F.pure(req.params.get(parameter))
        case FormOverrideStrategy(field, f) =>
          for {
            formFields <- f(
              UrlForm
                .entityDecoder[G]
                .decode(req, strict = true)
                .value
                .map(_.toOption.map(_.values))
            )
          } yield formFields.flatMap(_.get(field).flatMap(_.uncons.map(_._1)))
      }

    def processRequest(req: Request[G]): F[Response[G]] =
      getUnsafeOverrideMethod(req).flatMap {
        case Some(m: String) => parseMethod.andThen(processRequestWithMethod(req, _)).apply(m)
        case None => http(req)
      }

    Kleisli { (req: Request[G]) =>
      val isOverridden = config.overridableMethods.contains(req.method)
      if (isOverridden) processRequest(req) else http(req)
    }
  }
}
