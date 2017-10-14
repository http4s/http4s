package org.http4s
package internal

import scala.reflect.macros.whitebox.Context

private[http4s] object Macros {
  private def literalString(c: Context): String = {
    import c.universe._
    val Apply(_, List(Apply(_, List(Literal(Constant(s: String)))))) = c.prefix.tree
    s
  }

  def fragment(c: Context)(): c.Expr[Uri.Fragment] = {
    import c.universe._
    val s = literalString(c)
    Uri.Fragment.parse(s) match {
      case Right(_) => c.Expr(q"""org.http4s.HttpCodec[org.http4s.Uri.Fragment].parseOrThrow($s)""")
      case Left(e) => throw e
    }
  }

  def port(c: Context)(): c.Expr[Uri.Port] = {
    import c.universe._
    val s = literalString(c)
    Uri.Port.parse(s) match {
      case Right(_) => c.Expr(q"""org.http4s.HttpCodec[org.http4s.Uri.Port].parseOrThrow($s)""")
      case Left(e) => throw e
    }
  }

  def scheme(c: Context)(): c.Expr[Uri.Scheme] = {
    import c.universe._
    val s = literalString(c)
    Uri.Scheme.parse(s) match {
      case Right(_) => c.Expr(q"""org.http4s.HttpCodec[org.http4s.Uri.Scheme].parseOrThrow($s)""")
      case Left(e) => throw e
    }
  }

  def userInfo(c: Context)(): c.Expr[Uri.UserInfo] = {
    import c.universe._
    val s = literalString(c)
    Uri.UserInfo.parse(s) match {
      case Right(_) => c.Expr(q"""org.http4s.HttpCodec[org.http4s.Uri.UserInfo].parseOrThrow($s)""")
      case Left(e) => throw e
    }
  }
}
