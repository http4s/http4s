package org.http4s
package internal

import scala.reflect.macros.whitebox.Context

private[http4s] object Macros {
  private def literalString(c: Context): String = {
    import c.universe._
    val Apply(_, List(Apply(_, List(Literal(Constant(s: String)))))) = c.prefix.tree
    s
  }

  def fragment(c: Context)(): c.Expr[Fragment] = {
    import c.universe._
    val s = literalString(c)
    Fragment.parse(s) match {
      case Right(_) => c.Expr(q"""org.http4s.HttpCodec[org.http4s.Fragment].parseOrThrow($s)""")
      case Left(e) => throw e
    }
  }

  def scheme(c: Context)(): c.Expr[Scheme] = {
    import c.universe._
    val s = literalString(c)
    Scheme.parse(s) match {
      case Right(_) => c.Expr(q"""org.http4s.HttpCodec[org.http4s.Scheme].parseOrThrow($s)""")
      case Left(e) => throw e
    }
  }
}
