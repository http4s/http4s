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

import cats.Functor
import cats.effect.kernel.Sync
import fs2.compression._

private[middleware] trait GZipPlatform { this: GZip.type =>

  @deprecated("Use overload with Compression constraint", "0.23.15")
  def apply[F[_], G[_]](
      http: Http[F, G],
      bufferSize: Int,
      level: DeflateParams.Level,
      isZippable: Response[G] => Boolean,
      F: Functor[F],
      G: Sync[G],
  ): Http[F, G] =
    apply(http, bufferSize, level, isZippable)(F, Compression.forSync(G))

}
