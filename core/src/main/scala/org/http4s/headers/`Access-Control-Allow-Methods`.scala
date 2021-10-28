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

import cats.syntax.traverse._
import org.http4s.internal.parsing.Rfc7230
import org.http4s.{Header, Method, ParseResult}
import org.typelevel.ci.{CIString, CIStringSyntax}

/** The `Access-Control-Allow-Methods` header. */
final case class `Access-Control-Allow-Methods`(methods: List[Method])

object `Access-Control-Allow-Methods` {

  private[http4s] val parser =
    Rfc7230
      .headerRep(Rfc7230.token.map(CIString(_)))
      .mapFilter { list =>
        val parsedMethodList = list.traverse { ciMethod =>
          Method.fromString(ciMethod.toString).toOption
        }
        parsedMethodList.map(`Access-Control-Allow-Methods`.apply)
      }

  def parse(s: String): ParseResult[`Access-Control-Allow-Methods`] =
    ParseResult.fromParser(parser, "Invalid Access-Control-Allow-Methods header")(s)

  implicit val headerInstance: Header[`Access-Control-Allow-Methods`, Header.Recurring] =
    Header.createRendered(
      ci"Access-Control-Allow-Methods",
      _.methods,
      parse
    )

  // TODO Add the semigroup

}
