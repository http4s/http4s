package org.http4s.util

import cats.{Monoid, Order, Show}

/**
  * A String wrapper such that two strings `x` and `y` are equal if
  * `x.value.equalsIgnoreCase(y.value)`
  */
sealed class CaseInsensitiveString private (val value: String)
    extends CharSequence
    with Ordered[CaseInsensitiveString] {
  import CaseInsensitiveString._

  /* Lazily cache the hash code.  This is nearly identical to the
   * hashCode of java.lang.String, but converting to lower case on
   * the fly to avoid copying `value`'s character storage.
   */
  private[this] var hash = 0
  override def hashCode(): Int = {
    if (hash == 0) {
      hash = hashLower(value)
    }
    hash
  }

  override def equals(obj: Any): Boolean = obj match {
    case that: CaseInsensitiveString => value.equalsIgnoreCase(that.value)
    case _ => false
  }

  override def toString: String = value

  override def charAt(n: Int): Char =
    toString.charAt(n)

  override def length(): Int =
    value.length

  override def subSequence(start: Int, end: Int): CaseInsensitiveString =
    apply(value.subSequence(start, end))

  override def compare(other: CaseInsensitiveString): Int =
    value.compareToIgnoreCase(other.value)
}

object CaseInsensitiveString extends CaseInsensitiveStringInstances {
  val empty: CaseInsensitiveString =
    CaseInsensitiveString("")

  def apply(cs: CharSequence): CaseInsensitiveString =
    new CaseInsensitiveString(cs.toString)
}

private[http4s] sealed trait CaseInsensitiveStringInstances {
  implicit val http4sInstancesForCaseInsensitiveString: Monoid[CaseInsensitiveString] with Order[
    CaseInsensitiveString] with Show[CaseInsensitiveString] =
    new Monoid[CaseInsensitiveString] with Order[CaseInsensitiveString]
    with Show[CaseInsensitiveString] {
      def empty: CaseInsensitiveString =
        CaseInsensitiveString.empty
      def combine(f1: CaseInsensitiveString, f2: CaseInsensitiveString) =
        CaseInsensitiveString(f1.value + f2.value)

      def compare(x: CaseInsensitiveString, y: CaseInsensitiveString): Int =
        x.value.compare(y.value)
      override def eqv(x: CaseInsensitiveString, y: CaseInsensitiveString): Boolean =
        x == y

      override def show(x: CaseInsensitiveString): String =
        x.toString
    }
}
