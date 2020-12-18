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

object `Content-Disposition`
    extends HeaderKey.Internal[`Content-Disposition`]
    with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[`Content-Disposition`] =
    HttpHeaderParser.CONTENT_DISPOSITION(s)
}

// see http://tools.ietf.org/html/rfc2183
final case class `Content-Disposition`(dispositionType: String, parameters: Map[String, String])
    extends Header.Parsed {
  override def key: `Content-Disposition`.type = `Content-Disposition`
  override lazy val value = super.value
  override def renderValue(writer: Writer): writer.type = {
    writer.append(dispositionType)
    parameters.foreach(p => writer << "; " << p._1 << "=\"" << p._2 << '"')
    writer
  }
}
