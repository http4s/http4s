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

package org.http4s.headers

import cats.data.NonEmptyList
import cats.parse.Parser
import cats.parse.Parser0
import org.http4s.Header
import org.http4s._
import org.http4s.internal.parsing.CommonRules.headerRep1
import org.http4s.internal.parsing.CommonRules.ows
import org.http4s.internal.parsing.CommonRules.quotedString
import org.http4s.internal.parsing.CommonRules.token
import org.typelevel.ci._

import java.nio.charset.StandardCharsets

object Link {

  def apply(head: LinkValue, tail: LinkValue*): Link =
    apply(NonEmptyList(head, tail.toList))

  def parse(s: String): ParseResult[Link] =
    ParseResult.fromParser(parser, "Invalid Link header")(s)

  private[http4s] val parser: Parser[Link] = {
    import cats.parse.Parser._

    sealed trait LinkParam
    final case class Rel(value: String) extends LinkParam
    final case class Rev(value: String) extends LinkParam
    final case class Title(value: String) extends LinkParam
    final case class Type(value: MediaRange) extends LinkParam

    // https://datatracker.ietf.org/doc/html/rfc3986#section-4.1
    val linkValue: Parser0[LinkValue] =
      Uri.Parser.uriReference(StandardCharsets.UTF_8).map { uri =>
        headers.LinkValue(uri)
      }

    val linkParam: Parser0[LinkParam] = {
      val relParser = (string("rel=") *> token.orElse(quotedString))
        .map { rel =>
          Rel(rel)
        }

      val revParser = (string("rev=") *> token.orElse(quotedString)).map { rev =>
        Rev(rev)
      }

      val titleParser = (string("title=") *> token.orElse(quotedString)).map { title =>
        Title(title)
      }

      val typeParser = {
        val mediaRange = string("type=") *> MediaRange.parser.orElse(
          string("\"") *> MediaRange.parser <* string("\"")
        )
        mediaRange.map(tpe => Type(tpe))
      }

      relParser.orElse(revParser).orElse(titleParser).orElse(typeParser)
    }

    val linkValueWithAttr: Parser[LinkValue] =
      ((char('<') *> linkValue <* char('>')) ~ (char(';') *> ows *> linkParam).rep0).map {
        case (linkValue, linkParams) =>
          linkParams.foldLeft(linkValue) { case (lv, lp) =>
            lp match {
              case Rel(rel) =>
                if (lv.rel.isDefined) lv else lv.copy(rel = Some(rel))
              case Rev(rev) => lv.copy(rev = Some(rev))
              case Title(title) => lv.copy(title = Some(title))
              case Type(tpe) => lv.copy(`type` = Some(tpe))
            }
          }
      }

    headerRep1(linkValueWithAttr).map(links => Link(links.head, links.tail: _*))
  }

  implicit val headerInstance: Header[Link, Header.Recurring] =
    Header.createRendered(
      ci"Link",
      _.values,
      parse,
    )

  implicit val headerSemigroupInstance: cats.Semigroup[Link] =
    (a, b) => Link(a.values.concatNel(b.values))
}

final case class Link(values: NonEmptyList[LinkValue])
