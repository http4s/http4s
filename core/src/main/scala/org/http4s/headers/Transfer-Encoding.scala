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

import cats.data.NonEmptyList
import cats.parse.{Parser, Parser1}
import cats.syntax.all._
import org.http4s.internal.parsing.Rfc2616BasicRules.listSep

object `Transfer-Encoding`
    extends HeaderKey.Internal[`Transfer-Encoding`]
    with HeaderKey.Recurring {
  override def parse(s: String): ParseResult[`Transfer-Encoding`] =
    ParseResult.fromParser(parser, "Invalid Transfer-Encoding")(s)

  private[http4s] val parser: Parser1[`Transfer-Encoding`] =
    Parser.rep1Sep(TransferCoding.parser, 1, listSep).map(apply)
}

final case class `Transfer-Encoding`(values: NonEmptyList[TransferCoding])
    extends Header.RecurringRenderable {
  override def key: `Transfer-Encoding`.type = `Transfer-Encoding`
  def hasChunked: Boolean = values.exists(_ === TransferCoding.chunked)
  type Value = TransferCoding
}
