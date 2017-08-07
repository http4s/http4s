package org.http4s

import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context
import macrocompat.bundle
import org.http4s.internal.parboiled2.CharPredicate
import org.http4s.util.{Renderable, Writer}

sealed abstract case class FieldValue private (value: String) extends Renderable {
  override def render(w: Writer): w.type =
    w << value
}

object FieldValue {
  private val VCHAR = CharPredicate.Visible ++ CharPredicate(" ")

  def fromString(s: String): ParseResult[FieldValue] =
    if (isValidFieldValue(s)) ParseResult.success(new FieldValue(s) {})
    else ParseResult.fail("invalid field-value", s)

  def fromBoolean(b: Boolean): FieldValue =
    unsafeFromString(b.toString)

  def fromLong(l: Long): FieldValue =
    unsafeFromString(l.toString)

  def unsafeFromString(s: String): FieldValue =
    fromString(s).valueOr(throw _)

  def isValidFieldValue(s: String): Boolean =
    // FIXME This is a lie
    VCHAR.matchesAll(s)

  val empty: FieldValue = unsafeFromString("")
}
