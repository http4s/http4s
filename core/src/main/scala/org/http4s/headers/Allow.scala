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

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object Allow extends HeaderKey.Internal[Allow] with HeaderKey.Singleton {
  def apply(ms: Method*): Allow = Allow(ms.toSet)

  override def parse(s: String): ParseResult[Allow] =
    HttpHeaderParser.ALLOW(s)
}

/** A Response header that lists the methods that are supported by the target resource. Often
  * attached to responses with status
  * [[https://tools.ietf.org/html/rfc7231#section-6.5.5 405 Not Allowed]].
  *
  * [[https://tools.ietf.org/html/rfc7231#section-7.4.1 RFC-7231 Section 7.4.1 Allow]]
  */
final case class Allow(methods: Set[Method]) extends Header.Parsed {
  override def key: Allow.type = Allow
  override def renderValue(writer: Writer): writer.type =
    writer.addSet[Method](methods, sep = ", ")
}
