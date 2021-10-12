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

import cats.data.NonEmptyList
import org.http4s.internal.parsing.Rfc7230
import org.http4s.{Header, Method, ParseResult}
import org.typelevel.ci.{CIString, CIStringSyntax}

/** The `Access-Control-Allow-Methods` header. */
sealed trait `Access-Control-Allow-Methods`

object `Access-Control-Allow-Methods` {

  private val Wildcard: CIString = CIString("*")

  /** The value "*" only counts as a special wildcard value for requests without credentials
    * (requests without HTTP cookies or HTTP authentication information). In requests with
    * credentials, it is treated as the literal method name "*" without special semantics.
    */
  final case object AllMethods extends `Access-Control-Allow-Methods`

  /** A comma-delimited list of the allowed HTTP request methods.
    * @param methods List of allowed HTTP methods
    */
  final case class Methods(methods: NonEmptyList[Method]) extends `Access-Control-Allow-Methods`

  private[http4s] val parser =
    Rfc7230.headerRep1(Rfc7230.token.map(CIString(_)))
      .mapFilter { list =>
        if (containsOnlyTheWildcard(list)) {
          Some(AllMethods)
        } else if (list.exists(_ == Wildcard)) {
          Option.empty
        } else {
          val parsedMethodList = list.traverse { ciMethod =>
            Method.fromString(ciMethod.toString).toOption
          }
          parsedMethodList.map(Methods)
        }
      }

  private def containsOnlyTheWildcard(list: NonEmptyList[CIString]) = {
    list.size == 1 && list.head == Wildcard
  }

  def parse(s: String): ParseResult[`Access-Control-Allow-Methods`] =
    ParseResult.fromParser(parser, "Invalid Access-Control-Allow-Methods header")(s)

  implicit val headerInstance: Header[`Access-Control-Allow-Methods`, Header.Recurring] =
    Header.createRendered(
      ci"Access-Control-Allow-Methods",
      {
        case AllMethods => "*"
        case Methods(methods) => methods.toList.mkString(",")
      },
      parse
    )
}
