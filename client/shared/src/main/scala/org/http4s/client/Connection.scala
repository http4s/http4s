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

@deprecated("Is a Blaze detail.  Will be removed from public API.", "0.23.8")
trait Connection[F[_]] {

  /** Determine if the connection is closed and resources have been freed */
  def isClosed: Boolean

  /** Determine if the connection is in a state that it can be recycled for another request. */
  def isRecyclable: Boolean

  /** Close down the connection, freeing resources and potentially aborting a [[Response]] */
  def shutdown(): Unit

  /** The key for requests we are able to serve */
  def requestKey: RequestKey
}
