/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package parser

import org.specs2.mutable.Specification
import org.typelevel.ci.CIString

class HeaderParserSpec extends Specification {
  "Header parsing should catch ParseFailures " in {
    val h2 =
      Header.Raw(CIString("Date"), "Fri, 06 Feb 0010 15:28:43 GMT") // Invalid year: must be >= 1800
    h2.parsed must not(throwA[Exception])
  }
}
