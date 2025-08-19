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
import cats.parse.Rfc5234
import org.http4s.Header
import org.http4s.internal.parsing.Rfc7230
import org.http4s.util.Renderer
import org.http4s.util.Writer
import org.typelevel.ci._

// https://w3c.github.io/webappsec-referrer-policy/#referrer-policy-header

final case class `Referrer-Policy`(values: NonEmptyList[`Referrer-Policy`.Directive])

object `Referrer-Policy` {

  sealed abstract class Directive(val value: CIString)

  object Directive {
    def fromString(s: String): ParseResult[Directive] =
      ParseResult.fromParser(directiveParser, "Invalid Referrer-Policy directive")(s)

    implicit val rendererInstance: Renderer[Directive] =
      new Renderer[Directive] {
        def render(writer: Writer, dir: Directive): writer.type =
          writer << dir.value
      }
  }

  case object `no-referrer` extends Directive(ci"no-referrer")
  case object `no-referrer-when-downgrade` extends Directive(ci"no-referrer-when-downgrade")
  case object origin extends Directive(ci"origin")
  case object `origin-when-cross-origin` extends Directive(ci"origin-when-cross-origin")
  case object `same-origin` extends Directive(ci"same-origin")
  case object `strict-origin` extends Directive(ci"strict-origin")
  case object `strict-origin-when-cross-origin`
      extends Directive(ci"strict-origin-when-cross-origin")
  case object `unsafe-url` extends Directive(ci"unsafe-url")

  sealed abstract case class UnknownPolicy(name: CIString) extends Directive(name)

  object UnknownPolicy {
    private[http4s] def unsafeFromString(s: String): UnknownPolicy =
      new UnknownPolicy(CIString(s)) {}
  }

  private[http4s] val types: Map[CIString, Directive] =
    List(
      `no-referrer`,
      `no-referrer-when-downgrade`,
      origin,
      `origin-when-cross-origin`,
      `same-origin`,
      `strict-origin`,
      `strict-origin-when-cross-origin`,
      `unsafe-url`,
    )
      .map(i => (i.value, i))
      .toMap

  private val directiveParser: Parser[Directive] =
    Rfc5234.alpha.orElse(Parser.char('-')).rep.string.map(CIString(_)).map { s =>
      types.getOrElse(s, new UnknownPolicy(s) {})
    }

  private val parser: Parser[`Referrer-Policy`] =
    Rfc7230.headerRep1(directiveParser).map(apply)

  def parse(s: String): ParseResult[`Referrer-Policy`] =
    ParseResult.fromParser(parser, "Invalid Referrer-Policy header")(s)

  def apply(head: Directive, tail: Directive*): `Referrer-Policy` =
    apply(NonEmptyList(head, tail.toList))

  implicit val headerInstance: Header[`Referrer-Policy`, Header.Recurring] =
    Header.createRendered(
      ci"Referrer-Policy",
      _.values,
      parse,
    )

  implicit val headerSemigroupInstance: Semigroup[`Referrer-Policy`] =
    (a, b) => `Referrer-Policy`(a.values.concatNel(b.values))
}
