package org.http4s

import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context
import macrocompat.bundle
import org.http4s.internal.parboiled2.CharPredicate
import org.http4s.util.{Renderable, Writer}

sealed abstract case class FieldName private (value: String) extends Renderable {
  /* Lazily cache the hash code.  This is nearly identical to the
   * hashCode of java.lang.String, but converting to lower case on
   * the fly to avoid copying `value`'s character storage.
   */
  private[this] var hash = 0
  override def hashCode(): Int = {
    if (hash == 0) {
      var h = 0
      var i = 0
      val len = value.length
      while (i < len) {
        // Strings are equal igoring case if either their uppercase or lowercase
        // forms are equal. Equality of one does not imply the other, so we need
        // to go in both directions. A character is not guaranteed to make this
        // round trip, but it doesn't matter as long as all equal characters
        // hash the same.
        h = h * 31 + Character.toLowerCase(Character.toUpperCase(value.charAt(i)))
        i += 1
      }
      hash = h
    }
    hash
  }

  override def equals(obj: Any): Boolean = obj match {
    case that: FieldName => value.equalsIgnoreCase(that.value)
    case _ => false
  }

  override def render(w: Writer): w.type =
    w << value
}

object FieldName {
  private val VCHAR = CharPredicate.Visible
  private val tchar = VCHAR -- CharPredicate("""()<>@,;:\"/[]?={}""")

  def fromString(s: String): ParseResult[FieldName] =
    if (isValidFieldName(s)) ParseResult.success(new FieldName(s) {})
    else ParseResult.fail("invalid field-name", s)

  def unsafeFromString(s: String): FieldName =
    fromString(s).valueOr(throw _)

  def isValidFieldName(s: String): Boolean =
    s.nonEmpty && tchar.matchesAll(s)
}
