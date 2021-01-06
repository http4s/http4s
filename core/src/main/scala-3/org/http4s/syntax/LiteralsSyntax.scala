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
    def validate(s: String)(using Quotes): ParseResult[Expr[A]]
  }

  def validateUri(strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Quotes) =
    validate(uri, strCtxExpr, argsExpr)
  def validateUriScheme(strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Quotes) =
    validate(scheme, strCtxExpr, argsExpr)
  def validatePath(strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Quotes) =
    validate(path, strCtxExpr, argsExpr)
  def validateIpv4(strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Quotes) =
    validate(ipv4, strCtxExpr, argsExpr)
  def validateIpv6(strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Quotes) =
    validate(ipv6, strCtxExpr, argsExpr)
  def validateMediatype(strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Quotes) =
    validate(mediatype, strCtxExpr, argsExpr)
  def validateQvalue(strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Quotes) =
    validate(qvalue, strCtxExpr, argsExpr)

  def validate[A](validator: Validator[A], strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Quotes): Expr[A] = {
    val sc = strCtxExpr.unliftOrError
    validate(validator, sc.parts, argsExpr)
  }

  private def validate[A](validator: Validator[A], parts: Seq[String], argsExpr: Expr[Seq[Any]])(using Quotes): Expr[A] = {
    if (parts.size == 1) {
      val literal = parts.head
      validator.validate(literal) match {
        case Left(err) =>
          report.error(err.message)
          ???
        case Right(expr) => expr
      }
    } else {
      report.error("interpolation not supported", argsExpr)
      ???
    }
  }

  object uri extends Validator[Uri] {
    def validate(s: String)(using Quotes): ParseResult[Expr[Uri]] =
      Uri.fromString(s).map(value => Expr(value))
  }

  object scheme extends Validator[Uri.Scheme] {
    def validate(s: String): ParseResult[Expr[Uri.Scheme]] =
      Uri.Scheme.fromString(s).map(value => Expr(value))
  }

  object path extends Validator[Uri.Path] {
    def validate(s: String): ParseResult[Expr[Uri.Path]] =
      Uri.fromString(s).map(value => Expr(value.path))
  }

  object ipv4 extends Validator[Uri.Ipv4Address] {
    def validate(s: String): ParseResult[Expr[Uri.Ipv4Address]] =
      Uri.Ipv4Address.fromString(s).map(value => Expr(value))
  }

  object ipv6 extends Validator[Uri.Ipv6Address] {
    def validate(s: String): ParseResult[Expr[Uri.Ipv6Address]] =
      Uri.Ipv6Address.fromString(s).map(value => Expr(value))
  }

  object mediatype extends Validator[MediaType] {
    def validate(s: String): ParseResult[Expr[MediaType]] =
      MediaType.parse(s).map(value => Expr(value))
  }

  object qvalue extends Validator[QValue] {
    def validate(s: String): ParseResult[Expr[QValue]] =
      //QValue.fromString(s).map(value => Expr(value))
      ??? //todo: enable
  }
}
