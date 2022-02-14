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

import org.http4s.internal.parsing.Rfc7230
import org.typelevel.ci._

object `Access-Control-Request-Headers` {
  def apply(values: CIString*): `Access-Control-Request-Headers` =
    apply(values.toList)

  val empty: `Access-Control-Request-Headers` = `Access-Control-Request-Headers`(Nil)

  def parse(s: String): ParseResult[`Access-Control-Request-Headers`] =
    ParseResult.fromParser(parser, "Invalid Access-Control-Request-Headers header")(s)

  private[http4s] val parser =
    Rfc7230.headerRep(Rfc7230.token.map(CIString(_))).map(`Access-Control-Request-Headers`(_))

  implicit val headerInstance: Header[`Access-Control-Request-Headers`, Header.Recurring] =
    Header.createRendered(
      ci"Access-Control-Request-Headers",
      _.values,
      parse,
    )

  implicit val headerMonoidInstance: cats.Monoid[`Access-Control-Request-Headers`] =
    cats.Monoid.instance(empty, (a, b) => `Access-Control-Request-Headers`(a.values ++ b.values))
}

/** That request header is used by browsers when issuing a preflight request to let the server know which HTTP headers the client might send when the actual request is made.
  *
  * @see [[https://fetch.spec.whatwg.org/#http-access-control-request-headers HTTP extensions, CORS protocol, HTTP requests, Access-Control-Request-Headers]].
  */
final case class `Access-Control-Request-Headers`(values: List[CIString])
