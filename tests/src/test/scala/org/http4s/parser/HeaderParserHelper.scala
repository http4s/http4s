/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.parser

import org.http4s.{Header, ParseResult}

trait HeaderParserHelper[H <: Header] {
  def hparse(value: String): ParseResult[H]

  // Also checks to make sure whitespace doesn't effect the outcome
  protected def parse(value: String): H = {
    val a =
      hparse(value).fold(err => sys.error(s"Couldn't parse: '$value'.\nError: $err"), identity)
    val b =
      hparse(value.replace(" ", "")).fold(_ => sys.error(s"Couldn't parse: $value"), identity)
    assert(a == b, "Whitespace resulted in different headers")
    a
  }
}
