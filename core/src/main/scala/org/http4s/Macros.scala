package org.http4s

import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context
import macrocompat.bundle

@bundle
class Macros(val c: Context) {
  def fieldNameLiteral(args: c.Expr[Any]*): c.Expr[FieldName] = {
    import c.universe._
    c.prefix.tree match {
      case Apply(_, List(Apply(_, List(literal @Literal(Constant(text: String)))))) =>
        if (FieldName.isValidFieldName(text))
          reify(FieldName.unsafeFromString(c.Expr[String](literal).splice))
        else
          c.abort(c.enclosingPosition, s"Invalid field-name: $text")
      case _ =>
        c.abort(c.enclosingPosition, "can only validate literal field-names")
    }
  }

  def fieldValueLiteral(args: c.Expr[Any]*): c.Expr[FieldValue] = {
    import c.universe._
    try { FieldValue; () }
    catch { case t: ExceptionInInitializerError => t.getCause.printStackTrace() }
    c.prefix.tree match {
      case Apply(_, List(Apply(_, List(literal @Literal(Constant(text: String)))))) =>
        if (FieldValue.isValidFieldValue(text))
          reify(FieldValue.unsafeFromString(c.Expr[String](literal).splice))
        else
          c.abort(c.enclosingPosition, s"Invalid field-value: $text")
      case _ =>
        c.abort(c.enclosingPosition, "can only validate literal field-values")
    }
  }
}

