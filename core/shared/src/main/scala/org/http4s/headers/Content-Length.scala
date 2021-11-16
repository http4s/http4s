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

import org.http4s.parser.AdditionalRules
import org.typelevel.ci._

/** Constructs a `Content-Length` header.
  *
  * The HTTP RFCs do not specify a maximum length.  We have decided that `Long.MaxValue`
  * bytes ought to be good enough for anybody in order to avoid the irritations of `BigInt`.
  *
  * @param length the length
  */
final case class `Content-Length`(length: Long) {
  def modify(f: Long => Long): Option[`Content-Length`] =
    `Content-Length`.fromLong(f(length)).toOption
}

object `Content-Length` {
  val zero: `Content-Length` = apply(0)

  def fromLong(length: Long): ParseResult[`Content-Length`] =
    if (length >= 0L) ParseResult.success(apply(length))
    else ParseResult.fail("Invalid Content-Length", length.toString)

  def unsafeFromLong(length: Long): `Content-Length` =
    fromLong(length).fold(throw _, identity)

  def parse(s: String): ParseResult[`Content-Length`] =
    ParseResult.fromParser(parser, "Invalid Content-Length header")(s)

  private[http4s] val parser = AdditionalRules.NonNegativeLong.map(fromLong).mapFilter(_.toOption)

  val name: CIString = ci"Content-Length"

  implicit val headerInstance: Header[`Content-Length`, Header.Single] =
    Header.createRendered(
      name,
      _.length,
      parse,
    )

}
