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
import cats.parse.Parser
import org.http4s.internal.parsing.CommonRules
import org.http4s.util.Renderer
import org.http4s.util.Writer
import org.typelevel.ci._

object `Accept-Query` {
  def apply(head: MediaType, tail: MediaType*): `Accept-Query` =
    apply(NonEmptyList(head, tail.toList))

  def parse(s: String): ParseResult[`Accept-Query`] =
    ParseResult.fromParser(parser, "Invalid Accept-Query header")(s)

  private[http4s] val parser: Parser[`Accept-Query`] = {
    val quoted = (CommonRules.quotedString ~ MediaRange.mediaTypeExtensionParser.rep0).flatMap {
      case (mtStr, exts) =>
        MediaType.parse(mtStr) match {
          case Right(mt) => Parser.pure(if (exts.nonEmpty) mt.withExtensions(exts.toMap) else mt)
          case Left(failure) => Parser.failWith(failure.message)
        }
    }

    val item = quoted.orElse(MediaType.parser)

    CommonRules.headerRep1(item).map(`Accept-Query`(_))
  }

  implicit val renderer: Renderer[`Accept-Query`] = new Renderer[`Accept-Query`] {
    override def render(writer: Writer, t: `Accept-Query`): writer.type = {
      var first = true
      t.values.toList.foreach { mt =>
        if (!first) {
          writer << ", "
        }
        first = false
        val mtStr = s"${mt.mainType}/${mt.subType}"
        if (isSfToken(mtStr)) {
          writer << mtStr
        } else {
          writer.quote(mtStr)
        }
        mt.extensions.foreach { case (k, v) =>
          writer << ';' << k << '=' <<# v
        }
      }
      writer
    }
  }

  implicit val headerInstance: Header[`Accept-Query`, Header.Recurring] =
    Header.createRendered(
      ci"Accept-Query",
      identity,
      parse,
    )

  implicit val headerSemigroupInstance: cats.Semigroup[`Accept-Query`] =
    (a, b) => `Accept-Query`(a.values.concatNel(b.values))

  private def isSfToken(s: String): Boolean =
    if (s.isEmpty) false
    else {
      val first = s.charAt(0)
      val isFirstOk =
        (first >= 'a' && first <= 'z') || (first >= 'A' && first <= 'Z') || first == '*'
      if (!isFirstOk) false
      else {
        var i = 1
        var ok = true
        while (i < s.length && ok) {
          val c = s.charAt(i)
          val isCharOk = (c >= 'a' && c <= 'z') ||
            (c >= 'A' && c <= 'Z') ||
            (c >= '0' && c <= '9') ||
            c == '_' || c == '-' || c == '.' || c == '*' || c == '/' || c == ':' || c == '@'
          if (!isCharOk) ok = false
          i += 1
        }
        ok
      }
    }
}

final case class `Accept-Query`(values: NonEmptyList[MediaType])
