/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package parser

import org.http4s.headers.Origin

trait OriginHeader {
  def ORIGIN(value: String): ParseResult[Origin] = {
    Origin.parse(value)
  }

}
