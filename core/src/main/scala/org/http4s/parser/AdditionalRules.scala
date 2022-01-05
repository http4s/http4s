/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/parser/AdditionalRules.scala
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team
 *
 * Based on https://github.com/akka/akka-http/blob/5932237a86a432d623fafb1e84eeeff56d7485fe/akka-http-core/src/main/scala/akka/http/impl/model/parser/IpAddressParsing.scala
 * Copyright (C) 2009-2017 Lightbend Inc. <http://www.lightbend.com>
 */

package org.http4s
package parser

import cats.parse.{Parser0 => P0, Parser => P}
import org.http4s.internal.parsing.Rfc3986
import org.http4s.internal.parsing.Rfc7230

private[http4s] object AdditionalRules {
  def EOI: P[Unit] = P.char('\uFFFF')

  def EOL: P0[List[Unit]] = Rfc7230.ows *> EOI.rep0

  val NonNegativeLong: P[Long] = Rfc3986.digit.rep.string.mapFilter { s =>
    try Some(s.toLong)
    catch {
      case _: NumberFormatException => None
    }
  }

  val Long: P[Long] = Rfc7230.token.mapFilter { s =>
    try Some(s.toLong)
    catch {
      case _: NumberFormatException => None
    }
  }
}
