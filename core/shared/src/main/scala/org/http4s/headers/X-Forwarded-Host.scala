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

package org.http4s
package headers

object `X-Forwarded-Host` extends HeaderCompanion[`X-Forwarded-Host`]("X-Forwarded-Host") {
  def apply(host: String, port: Int): `X-Forwarded-Host` = apply(host, Some(port))

  private[http4s] val parser =
    Host.parser.map(host => apply(host.host, host.port))

  implicit val headerInstance: Header[`X-Forwarded-Host`, Header.Single] =
    createRendered(xfh => Host(xfh.host, xfh.port))
}

/** A Request header that provides forwarded host and possibly port
  * information.
  *
  * This header is not part of any specification. Whether or not this
  * header contains port information is unknown, so it is supported
  * just in case.
  *
  * [[https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Forwarded-Host MDN]]
  */
final case class `X-Forwarded-Host`(host: String, port: Option[Int] = None)
