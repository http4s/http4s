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

import cats.parse.{Parser => P}
import cats.parse.{Parser0 => P0}
import org.http4s.internal.parsing.CommonRules
import org.http4s.internal.parsing.Rfc3986

private[http4s] object AdditionalRules {
  def EOI: P[Unit] = P.char('\uFFFF')

  def EOL: P0[List[Unit]] = CommonRules.ows *> EOI.rep0

  val NonNegativeLong: P[Long] = Rfc3986.digit.rep.string.mapFilter { s =>
    try Some(s.toLong)
    catch {
      case _: NumberFormatException => None
    }
  }

  val Long: P[Long] = CommonRules.token.mapFilter { s =>
    try Some(s.toLong)
    catch {
      case _: NumberFormatException => None
    }
  }
}
