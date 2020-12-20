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
import cats.parse.{Parser, Parser1}
import cats.syntax.either._
import org.http4s.internal.parsing.Rfc7230.headerRep1
import org.http4s.parser.Rfc2616BasicRules.optWs
import org.http4s.util.{Renderable, Writer}

object Accept extends HeaderKey.Internal[Accept] with HeaderKey.Recurring {
  override def parse(s: String): ParseResult[Accept] =
    parser.parseAll(s).leftMap(e => ParseFailure("Invalid Accept header", e.toString))

  private[http4s] val parser: Parser1[Accept] = {
    import cats.parse.Parser._

    //TODO: take QValue.parser from the pull request #4013, once it is merged before this one
    //TODO: temp stub. Some unit tests fail
    val qValueParser: Parser[QValue] = Parser.pure(QValue.One)

    val acceptParams: Parser1[(QValue, Seq[(String, String)])] =
      (char(';') *> optWs *> char('q') *> char(
        '=') *> qValueParser ~ MediaType.mediaTypeExtension.rep).map { case (q, s) =>
        (
          q,
          s
        )
      }

    val qAndExtension: Parser1[(QValue, Seq[(String, String)])] =
      acceptParams.orElse1(MediaType.mediaTypeExtension.rep1.map { s =>
        (org.http4s.QValue.One, s.toList)
      })

    val fullRange: Parser1[MediaRangeAndQValue] = (MediaRange.parser ~ qAndExtension.?).map {
      case (mr, params) =>
        val (qValue, extensions) = params.getOrElse((QValue.One, Seq.empty))
        mr.withExtensions(extensions.toMap).withQValue(qValue)
    }

    headerRep1(fullRange).map(xs => Accept(xs.head, xs.tail: _*))
  }
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
    extends Header.RecurringRenderable {
  def key: Accept.type = Accept
  type Value = MediaRangeAndQValue
}
