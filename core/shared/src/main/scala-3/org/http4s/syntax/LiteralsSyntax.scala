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

trait LiteralsSyntax {
  extension (inline ctx: StringContext) {
    inline def uri(args: Any*): Uri = ${LiteralsSyntax.validateUri('{ctx}, '{args})}
    inline def path(args: Any*): Uri.Path = ${LiteralsSyntax.validatePath('{ctx}, '{args})}
    inline def scheme(args: Any*): Uri.Scheme = ${LiteralsSyntax.validateUriScheme('{ctx}, '{args})}
    inline def mediaType(args: Any*): MediaType = ${LiteralsSyntax.validateMediatype('{ctx}, '{args})}
    inline def qValue(args: Any*): QValue = ${LiteralsSyntax.validateQvalue('{ctx}, '{args})}
  }
}

private[syntax] object LiteralsSyntax {

  trait Validator[A] {
    def validate(s: String): Option[ParseFailure]
    def construct(s: String)(using Quotes): Expr[A]
  }

  def validateUri(strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Quotes) =
    validate(uri, strCtxExpr, argsExpr)
  def validateUriScheme(strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Quotes) =
    validate(urischeme, strCtxExpr, argsExpr)
  def validatePath(strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Quotes) =
    validate(uripath, strCtxExpr, argsExpr)
  def validateMediatype(strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Quotes) =
    validate(mediatype, strCtxExpr, argsExpr)
  def validateQvalue(strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Quotes) =
    validate(qvalue, strCtxExpr, argsExpr)

  def validate[A](validator: Validator[A], strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Type[A])(using Quotes): Expr[A] = {
    val sc = strCtxExpr.valueOrError
    validate(validator, sc.parts, argsExpr)
  }

  private def validate[A](validator: Validator[A], parts: Seq[String], argsExpr: Expr[Seq[Any]])(using Type[A])(using Quotes): Expr[A] = {
    if (parts.size == 1) {
      val literal = parts.head
      validator.validate(literal) match {
        case Some(err) =>
          quotes.reflect.report.throwError(err.message)
        case None => validator.construct(literal)
      }
    } else {
      quotes.reflect.report.throwError("interpolation not supported", argsExpr)
    }
  }

  object uri extends Validator[Uri] {
    override def validate(literal: String): Option[ParseFailure] = Uri.fromString(literal).swap.toOption

    override def construct(literal: String)(using Quotes): Expr[Uri] =
      '{Uri.unsafeFromString(${Expr(literal)})}
  }

  object urischeme extends Validator[Uri.Scheme] {
    override def validate(literal: String): Option[ParseFailure] = Uri.Scheme.fromString(literal).swap.toOption

    override def construct(literal: String)(using Quotes): Expr[Uri.Scheme] =
      '{Uri.Scheme.unsafeFromString(${Expr(literal)})}
  }

  object uripath extends Validator[Uri.Path] {
    override def validate(literal: String): Option[ParseFailure] = Uri.fromString(literal).map(_.path).swap.toOption

    override def construct(literal: String)(using Quotes): Expr[Uri.Path] =
      '{Uri.unsafeFromString(${Expr(literal)}).path}
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
