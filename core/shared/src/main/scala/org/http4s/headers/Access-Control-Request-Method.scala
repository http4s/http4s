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

package org.http4s.headers

import org.http4s._
import org.typelevel.ci._

final case class `Access-Control-Request-Method`(method: Method)

object `Access-Control-Request-Method` {
  def parse(s: String): ParseResult[`Access-Control-Request-Method`] =
    ParseResult.fromParser(parser, "Invalid Access-Control-Request-Method header")(s)

  private[http4s] val parser = Method.parser.map(apply)

  implicit val headerInstance: Header[`Access-Control-Request-Method`, Header.Single] =
    Header.create(
      ci"Access-Control-Request-Method",
      _.method.renderString,
      parse,
    )
}
