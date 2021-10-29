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

package org.http4s.ember.core

import cats.data.NonEmptyList
import org.http4s.Header
import org.http4s.Headers
import org.http4s.HttpVersion
import org.http4s.headers.Connection
import org.typelevel.ci._

import java.util.Locale
import scala.concurrent.duration._

private[ember] object Util {

  private[this] val closeCi = ci"close"
  private[this] val keepAliveCi = ci"keep-alive"
  private[this] val connectionCi = ci"connection"
  private[this] val close = Connection(NonEmptyList.of(closeCi))
  private[this] val keepAlive = Connection(NonEmptyList.one(keepAliveCi))

  def durationToFinite(duration: Duration): Option[FiniteDuration] = duration match {
    case f: FiniteDuration => Some(f)
    case _ => None
  }

  def connectionFor(httpVersion: HttpVersion, headers: Headers): Connection =
    if (isKeepAlive(httpVersion, headers)) keepAlive
    else close

  def isKeepAlive(httpVersion: HttpVersion, headers: Headers): Boolean = {
    // TODO: the problem is that any string that contains `expected` is admissible
    def hasConnection(expected: String): Boolean =
      headers.headers.exists { case Header.Raw(name, value) =>
        name == connectionCi && value.toLowerCase(Locale.ROOT).contains(expected)
      }

    httpVersion match {
      case HttpVersion.`HTTP/1.0` => hasConnection(keepAliveCi.toString)
      case HttpVersion.`HTTP/1.1` => !hasConnection(closeCi.toString)
      case _ => sys.error("unsupported http version")
    }
  }

}
