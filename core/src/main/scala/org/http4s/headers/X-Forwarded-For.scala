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

import cats.data.NonEmptyList
import cats.parse._
import com.comcast.ip4s.IpAddress
import org.http4s.internal.parsing.{Rfc3986, Rfc7230}
import org.http4s.util.{Renderable, Writer}
import org.typelevel.ci.CIString

object `X-Forwarded-For` extends HeaderKey.Internal[`X-Forwarded-For`] with HeaderKey.Recurring {
  override def parse(s: String): ParseResult[`X-Forwarded-For`] =
    ParseResult.fromParser(parser, "Invalid X-Forwarded-For header")(s)

  private[http4s] val parser: Parser[`X-Forwarded-For`] =
    Rfc7230
      .headerRep1(
        (Rfc3986.ipv4Address.backtrack | Rfc3986.ipv6Address)
          .map(s => Some(s)) | (Parser.string("unknown").as(None)))
      .map(`X-Forwarded-For`.apply)

  implicit val headerInstance: v2.Header[`X-Forwarded-For`, v2.Header.Single] =
    v2.Header.createRendered(
      CIString("X-Forwarded-For"),
      h =>
        new Renderable {
          def render(writer: Writer): writer.type = {
            h.values.head.fold(writer.append("unknown"))(i => writer.append(i.toString))
            h.values.tail.foreach(h.append(writer, _))
            writer
          }
        },
      ParseResult.fromParser(parser, "Invalid X-Forwarded-For header")
    )

}

final case class `X-Forwarded-For`(values: NonEmptyList[Option[IpAddress]])
    extends Header.Recurring {
  override def key: `X-Forwarded-For`.type = `X-Forwarded-For`
  type Value = Option[IpAddress]
  override lazy val value = super.value
  override def renderValue(writer: Writer): writer.type = {
    values.head.fold(writer.append("unknown"))(i => writer.append(i.toString))
    values.tail.foreach(append(writer, _))
    writer
  }

  @inline
  private def append(w: Writer, add: Option[IpAddress]): w.type = {
    w.append(", ")
    if (add.isDefined) w.append(add.get.toString)
    else w.append("unknown")
  }
}
