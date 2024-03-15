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

import cats.parse.Parser
import cats.parse.Parser.string
import org.http4s.util.Renderable
import org.http4s.util.Writer

object `X-DNS-Prefetch-Control`
    extends HeaderCompanion[`X-DNS-Prefetch-Control`]("X-DNS-Prefetch-Control") {

  case object On extends `X-DNS-Prefetch-Control`("on") {
    override def render(writer: Writer): writer.type =
      writer.append(Header.Raw(name, value).toString)
  }

  case object Off extends `X-DNS-Prefetch-Control`("off") {
    override def render(writer: Writer): writer.type =
      writer.append(Header.Raw(name, value).toString)
  }

  /*
   * `X-DNS-Prefetch-Control = on | off`
   */
  private[http4s] val parser: Parser[`X-DNS-Prefetch-Control`] = {
    val onParser = string("on").as(`X-DNS-Prefetch-Control`.On)
    val offParser = string("off").as(`X-DNS-Prefetch-Control`.Off)
    onParser | offParser
  }

  implicit val headerInstance: Header[`X-DNS-Prefetch-Control`, Header.Single] =
    createRendered(_.value)
}

/** A Response header that _controls DNS prefetching, a feature by which browsers proactively
  * perform domain name resolution on both links that the user may choose to follow as well
  * as URLs for items referenced by the document, including images, CSS, JavaScript, and so forth_.
  *
  * [[https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-DNS-Prefetch-Control]]
  */
sealed abstract class `X-DNS-Prefetch-Control`(val value: String) extends Renderable
