/*
 * Copyright 2019 http4s.org
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

package org.http4s.ember.client

import cats.effect._
import org.http4s._
import org.http4s.client._
import io.chrisdavenport.keypool._

final class EmberClient[F[_]] private[client] (
    private val client: Client[F],
    private val pool: KeyPool[F, RequestKey, (RequestKeySocket[F], F[Unit])]
)(implicit F: Bracket[F, Throwable])
    extends DefaultClient[F] {

  /** The reason for this extra class. This allows you to see the present state
    * of the underlying Pool, without having access to the pool itself.
    *
    * The first element represents total connections in the pool, the second
    * is a mapping between the number of connections in the pool for each requestKey.
    */
  def state: F[(Int, Map[RequestKey, Int])] = pool.state

  def run(req: Request[F]): Resource[F, Response[F]] = client.run(req)
}
