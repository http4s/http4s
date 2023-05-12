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

import cats.arrow.FunctionK
import cats.data.Kleisli
import cats.data.OptionT
import cats.effect._
import cats.syntax.all._
import cats.~>

/** [[Middleware]] for lifting application/x-www-form-urlencoded bodies into the
  * request query params.
  *
  * The params are merged into the existing paras _after_ the existing query params. This
  * means that if the query already contains the pair "foo" -> Some("bar"), parameters on
  * the body must be accessed through `multiParams`.
  */
object UrlFormLifter {
  def apply[F[_]: Sync, G[_]: Concurrent](f: G ~> F)(
      http: Kleisli[F, Request[G], Response[G]],
      strictDecode: Boolean = false,
  ): Kleisli[F, Request[G], Response[G]] =
    Kleisli { req =>
      def addUrlForm(form: UrlForm): F[Response[G]] = {
        val flatForm = form.values.toVector.flatMap { case (k, vs) =>
          vs.toVector.map(v => (k, Some(v)))
        }
        val params = req.uri.query.toVector ++ flatForm: Vector[(String, Option[String])]
        val newQuery = Query(params: _*)

        val newRequest = req
          .withUri(req.uri.copy(query = newQuery))
          .withAttributes(req.attributes)
          .withEmptyBody
        http(newRequest)
      }

      req.headers.get[headers.`Content-Type`] match {
        case Some(headers.`Content-Type`(MediaType.application.`x-www-form-urlencoded`, _))
            if checkRequest(req) =>
          for {
            decoded <- f(UrlForm.entityDecoder[G].decode(req, strictDecode).value)
            resp <- decoded.fold(
              mf => f(mf.toHttpResponse[G](req.httpVersion).pure[G]),
              addUrlForm,
            )
          } yield resp

        case _ => http(req)
      }
    }

  private def checkRequest[F[_]](req: Request[F]): Boolean =
    req.method == Method.POST || req.method == Method.PUT

  def httpRoutes[F[_]: Async](
      httpRoutes: HttpRoutes[F],
      strictDecode: Boolean = false,
  ): HttpRoutes[F] =
    apply(OptionT.liftK)(httpRoutes, strictDecode)

  def httpApp[F[_]: Async](httpApp: HttpApp[F], strictDecode: Boolean = false): HttpApp[F] =
    apply(FunctionK.id[F])(httpApp, strictDecode)
}
