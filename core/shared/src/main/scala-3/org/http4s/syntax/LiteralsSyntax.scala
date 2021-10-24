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

import scala.quoted._
import scala.language.`3.0`
import org.typelevel.literally.Literally

trait LiteralsSyntax {
  extension (inline ctx: StringContext) {
    inline def uri(inline args: Any*): Uri = ${LiteralsSyntax.UriLiteral('ctx, 'args)}
    inline def path(inline args: Any*): Uri.Path = ${LiteralsSyntax.UriPathLiteral('ctx, 'args)}
    inline def scheme(inline args: Any*): Uri.Scheme = ${LiteralsSyntax.UriSchemeLiteral('ctx, 'args)}
    inline def mediaType(args: Any*): MediaType = ${LiteralsSyntax.validateMediatype('{ctx}, '{args})}
    inline def qValue(args: Any*): QValue = ${LiteralsSyntax.validateQvalue('{ctx}, '{args})}
  }
}

private[syntax] object LiteralsSyntax {

  trait Validator[A] {
    def validate(s: String): Option[ParseFailure]
    def construct(s: String)(using Quotes): Expr[A]
  }

  def validateMediatype(strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Quotes) =
    validate(mediatype, strCtxExpr, argsExpr)
  def validateQvalue(strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Quotes) =
    validate(qvalue, strCtxExpr, argsExpr)

  def validate[A](validator: Validator[A], strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Type[A])(using Quotes): Expr[A] = {
    val sc = strCtxExpr.valueOrAbort
    validate(validator, sc.parts, argsExpr)
  }

  private def validate[A](validator: Validator[A], parts: Seq[String], argsExpr: Expr[Seq[Any]])(using Type[A])(using Quotes): Expr[A] = {
    if (parts.size == 1) {
      val literal = parts.head
      validator.validate(literal) match {
        case Some(err) =>
          quotes.reflect.report.errorAndAbort(err.message)
        case None => validator.construct(literal)
      }
    } else {
      quotes.reflect.report.errorAndAbort("interpolation not supported", argsExpr)
    }
  }

  object UriLiteral extends Literally[Uri] {
    def validate(s: String)(using Quotes) =
      Uri.fromString(s) match {
        case Left(parsingFailure) => Left(s"invalid URI: ${parsingFailure.details}")
        case Right(_) => Right('{Uri.unsafeFromString(${Expr(s)})})
      }
  }

  object UriSchemeLiteral extends Literally[Uri.Scheme] {
    def validate(s: String)(using Quotes) =
      Uri.Scheme.fromString(s) match {
        case Left(parsingFailure) => Left(s"invalid Scheme: ${parsingFailure.details}")
        case Right(_) => Right('{Uri.Scheme.unsafeFromString(${Expr(s)})})
      }
  }

  object UriPathLiteral extends Literally[Uri.Path] {
    def validate(s: String)(using Quotes) =
      Uri.fromString(s).map(_.path) match {
        case Left(parsingFailure) => Left(s"invalid Path: ${parsingFailure.details}")
        case Right(_) => Right('{Uri.Path.unsafeFromString(${Expr(s)})})
      }
  }

  object mediatype extends Validator[MediaType] {
    override def validate(literal: String): Option[ParseFailure] = MediaType.parse(literal).swap.toOption

    override def construct(literal: String)(using Quotes): Expr[MediaType] =
      '{MediaType.unsafeParse(${Expr(literal)})}
  }

  object qvalue extends Validator[QValue] {
    override def validate(literal: String): Option[ParseFailure] = QValue.fromString(literal).swap.toOption

    override def construct(literal: String)(using Quotes): Expr[QValue] =
      '{QValue.unsafeFromString(${Expr(literal)})}
  }
}
