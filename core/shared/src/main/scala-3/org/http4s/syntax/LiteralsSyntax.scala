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
package syntax

import org.typelevel.literally.Literally

import scala.language.`3.0`

trait LiteralsSyntax {
  extension (inline ctx: StringContext) {
    inline def uri(inline args: Any*): Uri = ${ LiteralsSyntax.UriLiteral('ctx, 'args) }
    inline def path(inline args: Any*): Uri.Path = ${ LiteralsSyntax.UriPathLiteral('ctx, 'args) }
    inline def scheme(inline args: Any*): Uri.Scheme = ${
      LiteralsSyntax.UriSchemeLiteral('ctx, 'args)
    }
    inline def mediaType(inline args: Any*): MediaType = ${
      LiteralsSyntax.MediaTypeLiteral('ctx, 'args)
    }
    inline def qValue(inline args: Any*): QValue = ${ LiteralsSyntax.QValueLiteral('ctx, 'args) }
  }
}

private[syntax] object LiteralsSyntax {
  object UriLiteral extends Literally[Uri] {
    def validate(s: String)(using Quotes) =
      Uri.fromString(s) match {
        case Left(parsingFailure) => Left(s"invalid URI: ${parsingFailure.details}")
        case Right(_) => Right('{ Uri.unsafeFromString(${ Expr(s) }) })
      }
  }

  object UriSchemeLiteral extends Literally[Uri.Scheme] {
    def validate(s: String)(using Quotes) =
      Uri.Scheme.fromString(s) match {
        case Left(parsingFailure) => Left(s"invalid Scheme: ${parsingFailure.details}")
        case Right(_) => Right('{ Uri.Scheme.unsafeFromString(${ Expr(s) }) })
      }
  }

  object UriPathLiteral extends Literally[Uri.Path] {
    def validate(s: String)(using Quotes) =
      Uri.fromString(s).map(_.path) match {
        case Left(parsingFailure) => Left(s"invalid Path: ${parsingFailure.details}")
        case Right(_) => Right('{ Uri.Path.unsafeFromString(${ Expr(s) }) })
      }
  }

  object MediaTypeLiteral extends Literally[MediaType] {
    def validate(s: String)(using Quotes) =
      MediaType.parse(s) match {
        case Left(parsingFailure) => Left(s"invalid MediaType: ${parsingFailure.details}")
        case Right(_) => Right('{ MediaType.unsafeParse(${ Expr(s) }) })
      }
  }

  object QValueLiteral extends Literally[QValue] {
    def validate(s: String)(using Quotes) =
      QValue.fromString(s) match {
        case Left(parsingFailure) => Left(s"invalid QValue: ${parsingFailure.details}")
        case Right(_) => Right('{ QValue.unsafeFromString(${ Expr(s) }) })
      }
  }
}
