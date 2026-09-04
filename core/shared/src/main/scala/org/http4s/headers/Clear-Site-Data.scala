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

import cats.Semigroup
import cats.data.NonEmptyList
import cats.parse.Parser
import org.http4s.Header
import org.http4s.internal.parsing.Rfc7230
import org.http4s.util.Renderer
import org.http4s.util.Writer
import org.typelevel.ci._

// https://w3c.github.io/webappsec-clear-site-data/#header

final case class `Clear-Site-Data`(values: NonEmptyList[`Clear-Site-Data`.Directive])

object `Clear-Site-Data` {

  sealed abstract class Directive(val value: String)

  object Directive {
    def fromString(s: String): ParseResult[Directive] =
      ParseResult.fromParser(directiveParser, "Invalid Clear-Site-Data directive")(s)

    implicit val rendererInstance: Renderer[Directive] =
      new Renderer[Directive] {
        def render(writer: Writer, dir: Directive): writer.type =
          writer << '"' << dir.value << '"'
      }
  }

  case object `*` extends Directive("*")
  case object cache extends Directive("cache")
  case object cookies extends Directive("cookies")
  case object storage extends Directive("storage")
  case object executionContexts extends Directive("executionContexts")

  sealed abstract case class UnknownType(name: String) extends Directive(name)

  object UnknownType {
    private[http4s] def unsafeFromString(s: String): UnknownType =
      new UnknownType(s) {}
  }

  private[http4s] val types: Map[String, Directive] =
    List(`*`, cache, cookies, storage, executionContexts)
      .map(i => (i.value, i))
      .toMap

  private val directiveParser: Parser[Directive] =
    Rfc7230.quotedString.map(s => types.getOrElse(s, UnknownType.unsafeFromString(s)))

  private val parser: Parser[`Clear-Site-Data`] =
    Rfc7230.headerRep1(directiveParser).map(apply)

  def parse(s: String): ParseResult[`Clear-Site-Data`] =
    ParseResult.fromParser(parser, "Invalid Clear-Site-Data header")(s)

  def apply(head: Directive, tail: Directive*): `Clear-Site-Data` =
    apply(NonEmptyList(head, tail.toList))

  implicit val headerInstance: Header[`Clear-Site-Data`, Header.Recurring] =
    Header.createRendered(
      ci"Clear-Site-Data",
      _.values,
      parse,
    )

  implicit val headerSemigroupInstance: Semigroup[`Clear-Site-Data`] =
    (a, b) => `Clear-Site-Data`(a.values.concatNel(b.values))
}
