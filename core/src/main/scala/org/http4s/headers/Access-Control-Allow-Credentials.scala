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

object `Access-Control-Allow-Credentials`
    extends HeaderKey.Internal[`Access-Control-Allow-Credentials`] {
  override def parse(s: String): ParseResult[`Access-Control-Allow-Credentials`] =
    HttpHeaderParser.ACCESS_CONTROL_ALLOW_CREDENTIALS(s)
}

// https://fetch.spec.whatwg.org/#http-access-control-allow-credentials
// This Header can only take the true value
final case class `Access-Control-Allow-Credentials`() extends Header.Parsed {
  override val value: String = "true"
  override def key: `Access-Control-Allow-Credentials`.type = `Access-Control-Allow-Credentials`
  override def renderValue(writer: Writer): writer.type =
    writer << value
}
