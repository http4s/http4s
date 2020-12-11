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

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object `Accept-Ranges` extends HeaderKey.Internal[`Accept-Ranges`] with HeaderKey.Singleton {
  def apply(first: RangeUnit, more: RangeUnit*): `Accept-Ranges` = apply((first +: more).toList)
  def bytes: `Accept-Ranges` = apply(RangeUnit.Bytes)
  def none: `Accept-Ranges` = apply(Nil)

  override def parse(s: String): ParseResult[`Accept-Ranges`] =
    HttpHeaderParser.ACCEPT_RANGES(s)
}

final case class `Accept-Ranges` private[http4s] (rangeUnits: List[RangeUnit])
    extends Header.Parsed {
  def key: `Accept-Ranges`.type = `Accept-Ranges`
  def renderValue(writer: Writer): writer.type =
    if (rangeUnits.isEmpty) writer.append("none")
    else {
      writer.append(rangeUnits.head)
      rangeUnits.tail.foreach(r => writer.append(", ").append(r))
      writer
    }
}
