/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package parser

import cats.syntax.all._
import org.http4s.internal.parboiled2._

/** Helper class that produces a `ParseResult` from the `main` target. */
private[http4s] abstract class Http4sParser[A](s: String, failureSummary: String) extends Parser {
  def input = s
  def main: Rule1[A]
  private[this] def target = rule(main ~ EOI)
  def parse =
    __run(target)(Parser.DeliveryScheme.Either)
      .leftMap(e => ParseFailure(failureSummary, e.format(s)))
}
