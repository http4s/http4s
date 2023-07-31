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

import cats.syntax.traverse._
import org.http4s.Header
import org.http4s.Method
import org.http4s.ParseResult
import org.http4s.internal.parsing.CommonRules
import org.typelevel.ci.CIString
import org.typelevel.ci.CIStringSyntax

object `Access-Control-Allow-Methods` {

  val name: CIString = ci"Access-Control-Allow-Methods"

  private[http4s] val parser =
    CommonRules
      .headerRep(CommonRules.token.map(CIString(_)))
      .mapFilter { list =>
        val parsedMethodList = list.traverse { ciMethod =>
          Method.fromString(ciMethod.toString).toOption
        }
        parsedMethodList.map(list => apply(list.toSet))
      }

  def parse(s: String): ParseResult[`Access-Control-Allow-Methods`] =
    ParseResult.fromParser(parser, "Invalid Access-Control-Allow-Methods header")(s)

  implicit val headerInstance: Header[`Access-Control-Allow-Methods`, Header.Recurring] =
    Header.createRendered(
      name,
      _.methods,
      parse,
    )

  implicit val headerSemigroupInstance: cats.Semigroup[`Access-Control-Allow-Methods`] =
    (a, b) => `Access-Control-Allow-Methods`(a.methods ++ b.methods)
}

/** The `Access-Control-Allow-Methods` header. */
final case class `Access-Control-Allow-Methods`(methods: Set[Method])
