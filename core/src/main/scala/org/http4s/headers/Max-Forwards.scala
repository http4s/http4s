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

/** Request header, used with the TRACE and OPTION request methods,
  * that gives an upper bound on how many times the request can be
  * forwarded by a proxy before it is rejected.
  *
  * [[https://tools.ietf.org/html/rfc7231#section-5.1.2 RFC-7231]]
  */
sealed abstract case class `Max-Forwards`(count: Long) extends Header.Parsed {
  override def key: `Max-Forwards`.type = `Max-Forwards`
  override def value: String = count.toString
  def renderValue(writer: Writer): writer.type = writer << value
}

object `Max-Forwards` extends HeaderKey.Internal[`Max-Forwards`] with HeaderKey.Singleton {
  private class MaxForwardsImpl(length: Long) extends `Max-Forwards`(length)

  val zero: `Max-Forwards` = new MaxForwardsImpl(0)

  def fromLong(length: Long): ParseResult[`Max-Forwards`] =
    if (length >= 0L) ParseResult.success(new MaxForwardsImpl(length))
    else ParseResult.fail("Invalid Max-Forwards", length.toString)

  def unsafeFromLong(length: Long): `Max-Forwards` =
    fromLong(length).fold(throw _, identity)

  override def parse(s: String): ParseResult[`Max-Forwards`] =
    HttpHeaderParser.MAX_FORWARDS(s)

}
