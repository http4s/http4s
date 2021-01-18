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
    def validate(s: String): ParseResult[A]
  }

  def validateUri(strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Quotes) =
    validate(s => Uri.fromString(s), strCtxExpr, argsExpr)
  def validateUriScheme(strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Quotes) =
    validate(s => Uri.Scheme.fromString(s), strCtxExpr, argsExpr)
  def validatePath(strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Quotes) =
    validate(s => Uri.fromString(s).map(_.path), strCtxExpr, argsExpr)
  def validateIpv4(strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Quotes) =
    validate(s => Uri.Ipv4Address.fromString(s), strCtxExpr, argsExpr)
  def validateIpv6(strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Quotes) =
    validate(s => Uri.Ipv6Address.fromString(s), strCtxExpr, argsExpr)
  def validateMediatype(strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Quotes) =
    validate(s => MediaType.parse(s), strCtxExpr, argsExpr)
  def validateQvalue(strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Quotes) =
    validate(s => QValue.fromString(s), strCtxExpr, argsExpr)

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
        case Right(result) => '{result}
      }
    } else {
      report.error("interpolation not supported", argsExpr)
      ???
    }
  }
}
