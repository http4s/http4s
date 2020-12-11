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
package parser

import cats.syntax.all._
import org.http4s.internal.parboiled2._

private[parser] abstract class Http4sHeaderParser[H <: Header](val input: ParserInput)
    extends Parser
    with AdditionalRules {
  def entry: Rule1[H]

  def parse: ParseResult[H] =
    entry
      .run()(Parser.DeliveryScheme.Either)
      .leftMap(e => ParseFailure("Invalid header", e.format(input)))
}
