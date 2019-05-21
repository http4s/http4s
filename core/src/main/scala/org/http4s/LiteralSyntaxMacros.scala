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
