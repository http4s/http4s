/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.parser

import org.http4s._
import org.http4s.headers.Referer

trait RefererHeader {
  def REFERER(value: String): ParseResult[Referer] = new RefererParser(value).parse

  private class RefererParser(value: String) extends UriHeaderParser[Referer](value) {
    override def fromUri(uri: Uri): Referer = Referer(uri)
  }
}
