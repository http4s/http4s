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
