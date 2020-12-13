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

object `Content-Location` extends HeaderKey.Internal[`Content-Location`] with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[`Content-Location`] =
    HttpHeaderParser.CONTENT_LOCATION(s)
}

/** {{{
  *   The "Content-Location" header field references a URI that can be used
  *   as an identifier for a specific resource corresponding to the
  *   representation in this message's payload
  * }}}
  * [[https://tools.ietf.org/html/rfc7231#section-3.1.4.2 RFC-7231 Section 3.1.4.2]]
  */
final case class `Content-Location`(uri: Uri) extends Header.Parsed {
  def key: `Content-Location`.type = `Content-Location`
  override def value: String = uri.toString
  def renderValue(writer: Writer): writer.type = writer << uri.toString
}
