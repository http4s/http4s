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

trait LiteralsSyntax {
  extension (inline ctx: StringContext) {
    inline def uri(args: Any*): Uri = ${LiteralsSyntax.validateUri('{ctx}, '{args})}
    inline def path(args: Any*): Uri.Path = ${LiteralsSyntax.validatePath('{ctx}, '{args})}
    inline def scheme(args: Any*): Uri.Scheme = ${LiteralsSyntax.validateUriScheme('{ctx}, '{args})}
    inline def ipv4(args: Any*): Uri.Ipv4Address = ${LiteralsSyntax.validateIpv4('{ctx}, '{args})}
    inline def ipv6(args: Any*): Uri.Ipv6Address = ${LiteralsSyntax.validateIpv6('{ctx}, '{args})}
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
  def validateIpv4(strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Quotes) =
    validate(ipv4, strCtxExpr, argsExpr)
  def validateIpv6(strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Quotes) =
    validate(ipv6, strCtxExpr, argsExpr)
  def validateMediatype(strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Quotes) =
    validate(mediatype, strCtxExpr, argsExpr)
  def validateQvalue(strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Quotes) =
    validate(qvalue, strCtxExpr, argsExpr)

  def validate[A](validator: Validator[A], strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Type[A])(using Quotes): Expr[A] = {
    val sc = strCtxExpr.unliftOrError
    validate(validator, sc.parts, argsExpr)
  }

  private def validate[A](validator: Validator[A], parts: Seq[String], argsExpr: Expr[Seq[Any]])(using Type[A])(using Quotes): Expr[A] = {
    if (parts.size == 1) {
      val literal = parts.head
      validator.validate(literal) match {
        case Some(err) =>
          report.throwError(err.message)
        case None => validator.construct(literal)
      }
    } else {
      report.throwError("interpolation not supported", argsExpr)
    }
  }

  object uri extends Validator[Uri] {
    override def validate(literal: String): Option[ParseFailure] = Uri.fromString(literal).swap.toOption

    override def construct(literal: String)(using Quotes): Expr[Uri] =
      '{_root_.org.http4s.Uri.unsafeFromString(${Expr(literal)})}
  }

  object urischeme extends Validator[Uri.Scheme] {
    override def validate(literal: String): Option[ParseFailure] = Uri.Scheme.fromString(literal).swap.toOption

    override def construct(literal: String)(using Quotes): Expr[Uri.Scheme] =
      '{_root_.org.http4s.Uri.Scheme.unsafeFromString(${Expr(literal)})}
  }

  object uripath extends Validator[Uri.Path] {
    override def validate(literal: String): Option[ParseFailure] = Uri.fromString(literal).map(_.path).swap.toOption

    override def construct(literal: String)(using Quotes): Expr[Uri.Path] =
      '{_root_.org.http4s.Uri.unsafeFromString(${Expr(literal)}).path}
  }

  object ipv4 extends Validator[Uri.Ipv4Address] {
    override def validate(literal: String): Option[ParseFailure] = Uri.Ipv4Address.fromString(literal).swap.toOption

    override def construct(literal: String)(using Quotes): Expr[Uri.Ipv4Address] =
      '{_root_.org.http4s.Uri.Ipv4Address.unsafeFromString(${Expr(literal)})}
  }

  object ipv6 extends Validator[Uri.Ipv6Address] {
    override def validate(literal: String): Option[ParseFailure] = Uri.Ipv6Address.fromString(literal).swap.toOption

    override def construct(literal: String)(using Quotes): Expr[Uri.Ipv6Address] =
      '{_root_.org.http4s.Uri.Ipv6Address.unsafeFromString(${Expr(literal)})}
  }

  object mediatype extends Validator[MediaType] {
    override def validate(literal: String): Option[ParseFailure] = MediaType.parse(literal).swap.toOption

    override def construct(literal: String)(using Quotes): Expr[MediaType] =
      '{_root_.org.http4s.MediaType.unsafeParse(${Expr(literal)})}
  }

  object qvalue extends Validator[QValue] {
    override def validate(literal: String): Option[ParseFailure] = QValue.fromString(literal).swap.toOption

    override def construct(literal: String)(using Quotes): Expr[QValue] =
      '{_root_.org.http4s.QValue.unsafeFromString(${Expr(literal)})}
  }
}
