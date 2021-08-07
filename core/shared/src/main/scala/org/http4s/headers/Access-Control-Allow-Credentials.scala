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
import org.typelevel.ci._

object `Access-Control-Allow-Credentials` {
  def parse(s: String): ParseResult[`Access-Control-Allow-Credentials`] =
    ParseResult.fromParser(parser, "Invalid Access-Control-Allow-Credentials header")(s)

  private[http4s] val parser = Parser.string("true").as(`Access-Control-Allow-Credentials`())

  implicit val headerInstance: Header[`Access-Control-Allow-Credentials`, Header.Single] =
    Header.create(
      ci"Access-Control-Allow-Credentials",
      _.value,
      parse
    )
}

// https://fetch.spec.whatwg.org/#http-access-control-allow-credentials
// This Header can only take the true value
final case class `Access-Control-Allow-Credentials`() {
  val value: String = "true"
}
