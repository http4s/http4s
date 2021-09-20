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
package client
package oauth1

import cats.effect.Async

private[oauth1] trait OAuth1Platform {
  private[oauth1] def makeSHASig[F[_]: Async](
      baseString: String,
      consumerSecret: String,
      tokenSecret: Option[String],
      algorithm: SignatureAlgorithm
  ): F[String] = Async[F].pure {
    val key = encode(consumerSecret) + "&" + tokenSecret.map(t => encode(t)).getOrElse("")
    algorithm.generate(baseString, key)
  }
}
