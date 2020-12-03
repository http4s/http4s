/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
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
