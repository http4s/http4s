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
package parser

import cats.data.NonEmptyList
import java.nio.charset.{Charset, StandardCharsets}
import org.http4s._
import org.http4s.headers.Origin
import org.http4s.internal.parboiled2._

trait OriginHeader {
  def ORIGIN(value: String): ParseResult[Origin] =
    new OriginParser(value).parse

  private class OriginParser(value: String)
      extends Http4sHeaderParser[Origin](value)
      with Rfc3986Parser {
    override def charset: Charset =
      StandardCharsets.ISO_8859_1

    def entry: Rule1[Origin] =
      rule {
        nullEntry | hostListEntry
      }

    // The spec states that an Origin may be the string "null":
    // http://tools.ietf.org/html/rfc6454#section-7
    //
    // However, this MDN article states that it may be the empty string:
    // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Origin
    //
    // Although the MDN article is possibly wrong,
    // it seems likely we could get either case,
    // so we read both as Origin.Null and re-serialize it as "null":
    def nullEntry: Rule1[Origin] =
      rule {
        (str("") ~ EOI | str("null") ~ EOI) ~> { () =>
          Origin.Null
        }
      }

    def hostListEntry: Rule1[Origin] =
      rule {
        (host ~ zeroOrMore(" " ~ host)) ~> {
          (head: Origin.Host, tail: collection.Seq[Origin.Host]) =>
            Origin.HostList(NonEmptyList(head, tail.toList))
        }
      }

    def host: Rule1[Origin.Host] =
      rule {
        (scheme ~ "://" ~ Host ~ Port) ~> { (s, h, p) =>
          Origin.Host(s, h, p)
        }
      }
  }
}
