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

object Authorization extends HeaderKey.Internal[Authorization] with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[Authorization] =
    HttpHeaderParser.AUTHORIZATION(s)

  def apply(basic: BasicCredentials): Authorization =
    Authorization(Credentials.Token(AuthScheme.Basic, basic.token))
}

final case class Authorization(credentials: Credentials) extends Header.Parsed {
  override def key: `Authorization`.type = `Authorization`
  override def renderValue(writer: Writer): writer.type = credentials.render(writer)
}
