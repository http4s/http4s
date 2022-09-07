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

import org.http4s.Uri.Authority
import org.http4s.Uri.Scheme

/** Represents a key for requests that can conceivably share a [[Connection]]. */
final case class RequestKey(scheme: Scheme, authority: Authority) {
  override def toString: String = s"${scheme.value}://${authority}"
}

object RequestKey {
  def fromRequest[F[_]](request: Request[F]): RequestKey = {
    val uri = request.uri
    RequestKey(uri.scheme.getOrElse(Scheme.http), uri.authority.getOrElse(Authority()))
  }
}
