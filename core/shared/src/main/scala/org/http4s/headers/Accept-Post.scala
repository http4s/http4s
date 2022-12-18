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

import cats.parse.Parser0
import org.http4s.internal.parsing.CommonRules
import org.typelevel.ci._
//Accept-Post response header.
//See https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-Post
object `Accept-Post` {
  def apply(values: MediaType*): `Accept-Post` = apply(values.toList)

  def parse(s: String): ParseResult[`Accept-Post`] =
    ParseResult.fromParser(parser, "Invalid Accept-Post header")(s)

  private[http4s] val parser: Parser0[`Accept-Post`] =
    CommonRules.headerRep(MediaType.parser).map(`Accept-Post`(_))

  implicit val headerInstance: Header[`Accept-Post`, Header.Recurring] =
    Header.createRendered(
      ci"Accept-Post",
      _.values,
      parse,
    )

  implicit val headerSemigroupInstance: cats.Monoid[`Accept-Post`] =
    cats.Monoid.instance(`Accept-Post`(Nil), (one, two) => `Accept-Post`(one.values ++ two.values))
}

final case class `Accept-Post`(values: List[MediaType])
