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

package org.http4s.client.middleware

import cats.effect._
import org.http4s._
import org.http4s.client.Client
import org.typelevel.vault._

/** Client middleware that sets the destination attribute of every request to the specified value.
  */
object DestinationAttribute {
  def apply[F[_]: Async](client: Client[F], destination: String): Client[F] =
    Client { req =>
      client.run(req.withAttribute(Destination, destination))
    }

  /** The returned function can be used as classifier function when creating the [[Metrics]] middleware, to use the destination
    * attribute from the request as classifier.
    *
    * @return the classifier function
    */
  def getDestination[F[_]](): Request[F] => Option[String] = _.attributes.lookup(Destination)

  val Destination: Key[String] = Key.newKey[SyncIO, String].unsafeRunSync()

  val EmptyDestination: String = ""
}
