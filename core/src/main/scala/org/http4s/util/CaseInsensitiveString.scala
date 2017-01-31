package org.http4s.util

import java.util.Locale

sealed class CaseInsensitiveString private (val value: String) extends CharSequence {
  import CaseInsensitiveString._

  private var hash = 0

  override def hashCode(): Int = {
    if (hash == 0 && value.length > 0) {
      hash = value.toLowerCase(Locale.ROOT).hashCode
    }
    hash
  }

  override def equals(obj: Any): Boolean = obj match {
    case that: CaseInsensitiveString => value.equalsIgnoreCase(that.value)
    case _ => false
  }

  override def toString: String = value

  def length(): Int = value.length

  def charAt(index: Int): Char = value.charAt(index)

  def subSequence(start: Int, end: Int): CaseInsensitiveString = apply(value.subSequence(start, end))
}

object CaseInsensitiveString {
  def apply(cs: CharSequence): CaseInsensitiveString =
    new CaseInsensitiveString(cs.toString)
}
