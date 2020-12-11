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

object `X-B3-Sampled` extends HeaderKey.Internal[`X-B3-Sampled`] with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[`X-B3-Sampled`] =
    HttpHeaderParser.X_B3_SAMPLED(s)
}

final case class `X-B3-Sampled`(sampled: Boolean) extends Header.Parsed {
  override def key: `X-B3-Sampled`.type = `X-B3-Sampled`

  override def renderValue(writer: Writer): writer.type = {
    val b: String = if (sampled) "1" else "0"
    writer.append(b)
  }
}
