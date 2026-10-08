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

package org.http4s.metrics

import org.http4s.Request
import org.http4s.RequestPrelude
import org.typelevel.vault.Vault

/** A body-independent snapshot of a request for recording metrics.
  *
  * Unlike [[RequestPrelude]], this snapshot retains request attributes, including server-side
  * connection information when supplied by the server backend. Attributes added by inner
  * middleware after this snapshot is created are not visible.
  */
sealed trait MetricsRequest {
  def requestPrelude: RequestPrelude

  def attributes: Vault

  final def connectionInfo: Option[Request.Connection] =
    attributes.lookup(Request.Keys.ConnectionInfo)
}

object MetricsRequest {

  private[http4s] def fromRequest[F[_]](request: Request[F]): MetricsRequest =
    new Impl(request.requestPrelude, request.attributes)

  private final class Impl(
      val requestPrelude: RequestPrelude,
      val attributes: Vault,
  ) extends MetricsRequest
}
