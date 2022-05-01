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

import org.typelevel.ci._

object `Content-Encoding` {
  def parse(s: String): ParseResult[`Content-Encoding`] =
    ParseResult.fromParser(parser, "Invalid Content-Encoding header")(s)

  private[http4s] val parser = ContentCoding.parser.map(`Content-Encoding`(_))

  implicit val headerInstance: Header[`Content-Encoding`, Header.Single] =
    Header.createRendered(
      ci"Content-Encoding",
      _.contentCoding,
      parse,
    )
}

final case class `Content-Encoding`(contentCoding: ContentCoding)
