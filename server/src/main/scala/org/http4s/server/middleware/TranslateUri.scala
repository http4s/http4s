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
package server.middleware

import cats.data.Kleisli
import cats.{Functor, MonoidK}

/** Removes the given prefix from the beginning of the path of the [[Request]].
  */
object TranslateUri {
  def apply[F[_], G[_], B](prefix: String)(http: Kleisli[F, Request[G], B])(implicit
      F: MonoidK[F],
      G: Functor[G]): Kleisli[F, Request[G], B] =
    if (prefix.isEmpty || prefix == "/") http
    else {
      val (slashedPrefix, unslashedPrefix) =
        if (prefix.startsWith("/")) (prefix, prefix.substring(1))
        else (s"/$prefix", prefix)

      val newCaret = slashedPrefix.length

      Kleisli { (req: Request[G]) =>
        val shouldTranslate =
          req.pathInfo.startsWith(unslashedPrefix) || req.pathInfo.startsWith(slashedPrefix)

        if (shouldTranslate) http(setCaret(req, newCaret))
        else F.empty
      }
    }

  private def setCaret[F[_]: Functor](req: Request[F], newCaret: Int): Request[F] = {
    val oldCaret = req.attributes
      .lookup(Request.Keys.PathInfoCaret)
      .getOrElse(0)
    req.withAttribute(Request.Keys.PathInfoCaret, oldCaret + newCaret)
  }
}
