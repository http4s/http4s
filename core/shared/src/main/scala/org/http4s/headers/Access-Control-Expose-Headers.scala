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

object `Access-Control-Expose-Headers` {
  def apply(values: CIString*): `Access-Control-Expose-Headers` =
    apply(values.toList)

  val empty: `Access-Control-Expose-Headers` = `Access-Control-Expose-Headers`(Nil)

  def parse(s: String): ParseResult[`Access-Control-Expose-Headers`] =
    ParseResult.fromParser(parser, "Invalid Access-Control-Allow-Headers header")(s)

  // https://fetch.spec.whatwg.org/#http-new-header-syntax (as of commit 613aad98fb19bf44fc95c4e9a332706d68795da8)
  private[http4s] val parser =
    Rfc7230.headerRep(Rfc7230.token.map(CIString(_))).map(`Access-Control-Expose-Headers`(_))

  implicit val headerInstance: Header[`Access-Control-Expose-Headers`, Header.Recurring] =
    Header.createRendered(
      ci"Access-Control-Expose-Headers",
      _.values,
      parse,
    )

  implicit val headerMonoidInstance: cats.Monoid[`Access-Control-Expose-Headers`] =
    cats.Monoid.instance(empty, (a, b) => `Access-Control-Expose-Headers`(a.values ++ b.values))
}

final case class `Access-Control-Expose-Headers`(values: List[CIString])
