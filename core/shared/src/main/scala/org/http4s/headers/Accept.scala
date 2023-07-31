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
import org.http4s.internal.parsing.CommonRules.headerRep1
import org.http4s.util.Renderable
import org.http4s.util.Writer
import org.typelevel.ci._

object Accept {
  def apply(head: MediaRangeAndQValue, tail: MediaRangeAndQValue*): Accept =
    apply(NonEmptyList(head, tail.toList))

  def parse(s: String): ParseResult[Accept] =
    ParseResult.fromParser(parser, "Invalid Accept header")(s)

  private[http4s] val parser: Parser[Accept] = {
    val acceptParams =
      (QValue.parser ~ MediaRange.mediaTypeExtensionParser.rep0).map { case (qValue, ext) =>
        (
          qValue,
          ext,
        )
      }

    val qAndExtension =
      acceptParams.orElse(MediaRange.mediaTypeExtensionParser.rep.map { s =>
        (org.http4s.QValue.One, s.toList)
      })

    val fullRange: Parser[MediaRangeAndQValue] = (MediaRange.parser ~ qAndExtension.?).map {
      case (mr, params) =>
        val (qValue, extensions) = params.getOrElse((QValue.One, Seq.empty))
        mr.withExtensions(extensions.toMap).withQValue(qValue)
    }

    headerRep1(fullRange).map(Accept(_))
  }

  implicit val headerInstance: Header[Accept, Header.Recurring] =
    Header.createRendered(
      ci"Accept",
      _.values,
      parse,
    )

  implicit val headerSemigroupInstance: cats.Semigroup[Accept] =
    (a, b) => Accept(a.values.concatNel(b.values))
}

final case class MediaRangeAndQValue(mediaRange: MediaRange, qValue: QValue = QValue.One)
    extends Renderable {
  def render(writer: Writer): writer.type = {
    writer << mediaRange.withExtensions(Map.empty) << qValue
    MediaRange.renderExtensions(writer, mediaRange)
    writer
  }
}

object MediaRangeAndQValue {
  implicit def withDefaultQValue(mediaRange: MediaRange): MediaRangeAndQValue =
    MediaRangeAndQValue(mediaRange, QValue.One)
}

final case class Accept(values: NonEmptyList[MediaRangeAndQValue])
