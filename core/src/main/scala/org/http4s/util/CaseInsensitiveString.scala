package org.http4s.util

import java.util.Locale

sealed class CaseInsensitiveString private (val value: String) extends CharSequence {
  import CaseInsensitiveString._

  private lazy val folded = value.toLowerCase(Locale.ROOT)

  override def hashCode(): Int = folded.##

  override def equals(obj: Any): Boolean = obj match {
    case that: CaseInsensitiveString => value.equalsIgnoreCase(that.value)
    case _ => false
  }

  override def toString: String = value

  def length(): Int = value.length

  def charAt(index: Int): Char = value.charAt(index)

  def subSequence(start: Int, end: Int): CaseInsensitiveString = apply(value.subSequence(start, end))
}

object CaseInsensitiveString extends CaseInsensitiveStringSyntax {
  def apply(cs: CharSequence) = new CaseInsensitiveString(cs.toString)
}

final class CaseInsensitiveStringOps(val self: CharSequence) extends AnyVal {
  def ci: CaseInsensitiveString = CaseInsensitiveString(self)
}

trait CaseInsensitiveStringSyntax {
  implicit def ToCaseInsensitiveStringSyntax(cs: CharSequence): CaseInsensitiveStringOps =
    new CaseInsensitiveStringOps(cs)
}

