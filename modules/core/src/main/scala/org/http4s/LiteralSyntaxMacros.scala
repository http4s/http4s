/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import scala.reflect.macros.blackbox

/** Thanks to ip4s for the singlePartInterpolator implementation
  * https://github.com/Comcast/ip4s/blob/b4f01a4637f2766a8e12668492a3814c478c6a03/shared/src/main/scala/com/comcast/ip4s/LiteralSyntaxMacros.scala
  */
object LiteralSyntaxMacros {
  def uriInterpolator(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Uri] =
    singlePartInterpolator(c)(
      args,
      "Uri",
      Uri.fromString(_).isRight,
      s => c.universe.reify(Uri.unsafeFromString(s.splice)))

  def pathInterpolator(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Uri.Path] =
    singlePartInterpolator(c)(
      args,
      "Uri.Path",
      _ => true,
      s => c.universe.reify(Uri.Path.fromString(s.splice)))

  def schemeInterpolator(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Uri.Scheme] =
    singlePartInterpolator(c)(
      args,
      "Scheme",
      Uri.Scheme.fromString(_).isRight,
      s => c.universe.reify(Uri.Scheme.unsafeFromString(s.splice)))

  def ipv4AddressInterpolator(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Uri.Ipv4Address] =
    singlePartInterpolator(c)(
      args,
      "Ipv4Address",
      Uri.Ipv4Address.fromString(_).isRight,
      s => c.universe.reify(Uri.Ipv4Address.unsafeFromString(s.splice)))

  def ipv6AddressInterpolator(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Uri.Ipv6Address] =
    singlePartInterpolator(c)(
      args,
      "Ipv6Address",
      Uri.Ipv6Address.fromString(_).isRight,
      s => c.universe.reify(Uri.Ipv6Address.unsafeFromString(s.splice)))

  def mediaTypeInterpolator(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[MediaType] =
    singlePartInterpolator(c)(
      args,
      "MediaType",
      MediaType.parse(_).isRight,
      s => c.universe.reify(MediaType.unsafeParse(s.splice)))

  def qValueInterpolator(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[QValue] =
    singlePartInterpolator(c)(
      args,
      "QValue",
      QValue.fromString(_).isRight,
      s => c.universe.reify(QValue.unsafeFromString(s.splice)))

  private def singlePartInterpolator[A](c: blackbox.Context)(
      args: Seq[c.Expr[Any]],
      typeName: String,
      validate: String => Boolean,
      construct: c.Expr[String] => c.Expr[A]): c.Expr[A] = {
    import c.universe._
    identity(args)
    c.prefix.tree match {
      case Apply(_, List(Apply(_, (lcp @ Literal(Constant(p: String))) :: Nil))) =>
        val valid = validate(p)
        if (valid) construct(c.Expr(lcp))
        else c.abort(c.enclosingPosition, s"invalid $typeName")
    }
  }
}
