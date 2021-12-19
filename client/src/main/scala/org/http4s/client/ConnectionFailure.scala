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

package org.http4s.client

import java.io.IOException
import java.net.InetSocketAddress

/** Indicates a failure to establish a client connection, preserving the request key
  * that we tried to connect to.
  */
class ConnectionFailure(
    val requestKey: RequestKey,
    val upstream: InetSocketAddress,
    val cause: Throwable,
) extends IOException(cause) {
  override def getMessage(): String =
    s"Error connecting to $requestKey using address ${upstream.getHostString}:${upstream.getPort} (unresolved: ${upstream.isUnresolved})"
}

object ConnectionFailure {
  def unapply(failure: ConnectionFailure): Option[(RequestKey, InetSocketAddress, Throwable)] =
    Some((failure.requestKey, failure.upstream, failure.cause))
}
