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

import cats.Functor
import cats.data.Kleisli

@deprecated("Use org.http4s.server.middleware.TranslateUri instead", since = "0.18.16")
object URITranslation {
  def translateRoot[F[_], G[_]: Functor, B](prefix: String)(
      @deprecatedName(Symbol("service")) http: Kleisli[F, Request[G], B])
      : Kleisli[F, Request[G], B] = {
    val newCaret = prefix match {
      case "/" => 0
      case x if x.startsWith("/") => x.length
      case x => x.length + 1
    }

    http.local { (req: Request[G]) =>
      val oldCaret = req.attributes
        .lookup(Request.Keys.PathInfoCaret)
        .getOrElse(0)
      req.withAttribute(Request.Keys.PathInfoCaret, oldCaret + newCaret)
    }
  }
}
