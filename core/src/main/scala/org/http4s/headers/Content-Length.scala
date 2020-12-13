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

/** Constructs a `Content-Length` header.
  *
  * The HTTP RFCs do not specify a maximum length.  We have decided that `Long.MaxValue`
  * bytes ought to be good enough for anybody in order to avoid the irritations of `BigInt`.
  *
  * @param length the length
  */
sealed abstract case class `Content-Length`(length: Long) extends Header.Parsed {
  override def key: `Content-Length`.type = `Content-Length`
  override def renderValue(writer: Writer): writer.type = writer.append(length)
  def modify(f: Long => Long): Option[`Content-Length`] =
    `Content-Length`.fromLong(f(length)).toOption
}

object `Content-Length` extends HeaderKey.Internal[`Content-Length`] with HeaderKey.Singleton {
  private class ContentLengthImpl(length: Long) extends `Content-Length`(length)

  val zero: `Content-Length` = new ContentLengthImpl(0)

  def fromLong(length: Long): ParseResult[`Content-Length`] =
    if (length >= 0L) ParseResult.success(new ContentLengthImpl(length))
    else ParseResult.fail("Invalid Content-Length", length.toString)

  def unsafeFromLong(length: Long): `Content-Length` =
    fromLong(length).fold(throw _, identity)

  def parse(s: String): ParseResult[`Content-Length`] =
    HttpHeaderParser.CONTENT_LENGTH(s)
}
