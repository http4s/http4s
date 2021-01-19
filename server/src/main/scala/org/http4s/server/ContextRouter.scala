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

package org.http4s.server

import org.http4s.{ContextRequest, ContextRoutes, Uri}
import cats.data.Kleisli
import cats.effect.Sync
import cats.syntax.semigroupk._

object ContextRouter {

  /** Defines an [[ContextRoutes]] based on list of mappings.
    * @see define
    */
  def apply[F[_]: Sync, A](mappings: (String, ContextRoutes[A, F])*): ContextRoutes[A, F] =
    define(mappings: _*)(ContextRoutes.empty[A, F])

  /** Defines an [[ContextRoutes]] based on list of mappings and
    * a default Service to be used when none in the list match incoming requests.
    *
    * The mappings are processed in descending order (longest first) of prefix length.
    */
  def define[F[_]: Sync, A](
      mappings: (String, ContextRoutes[A, F])*
  )(default: ContextRoutes[A, F]): ContextRoutes[A, F] =
    mappings.sortBy(_._1.length).foldLeft(default) { case (acc, (prefix, routes)) =>
      val prefixSegments = Uri.Path.fromString(prefix)
      if (prefixSegments.isEmpty) routes <+> acc
      else
        Kleisli { req =>
          (
            if (req.req.pathInfo.startsWith(prefixSegments))
              routes
                .local[ContextRequest[F, A]](r =>
                  ContextRequest(r.context, Router.translate(prefixSegments)(r.req))) <+> acc
            else
              acc
          )(req)
        }
    }
}
