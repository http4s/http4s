package org.http4s.util

import java.util.Locale
import scala.collection.generic.CanBuildFrom
import scala.collection.{AbstractSeq, IndexedSeqOptimized}
import scala.collection.immutable.{IndexedSeq, StringLike, WrappedString}
import scala.collection.mutable.{Builder, StringBuilder}
import scala.reflect.ClassTag

/**
 * A String wrapper such that two strings `x` and `y` are equal if
 * `x.value.equalsIgnoreCase(y.value)`
 */
sealed class CaseInsensitiveString private (val value: String)
    extends IndexedSeq[Char]
    with IndexedSeqOptimized[Char, CaseInsensitiveString]
    with Ordered[CaseInsensitiveString] {
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

  def apply(n: Int): Char =
    toString.charAt(n)
  def charAt(n: Int): Char =
    apply(n)

  def length: Int =
    value.length

  override def mkString: String =
    value

  override def compare(other: CaseInsensitiveString): Int =
    value.compareToIgnoreCase(other.value)

  override protected[this] def newBuilder =
    CaseInsensitiveString.newBuilder
  override protected[this] def thisCollection: CaseInsensitiveString =
    this
  override protected[this] def toCollection(repr: CaseInsensitiveString): CaseInsensitiveString =
    repr
}

object CaseInsensitiveString {
  val empty: CaseInsensitiveString =
    CaseInsensitiveString("")

  def apply(cs: CharSequence): CaseInsensitiveString =
    new CaseInsensitiveString(cs.toString)

  implicit def canBuildFrom: CanBuildFrom[CaseInsensitiveString, Char, CaseInsensitiveString] =
    new CanBuildFrom[CaseInsensitiveString, Char, CaseInsensitiveString] {
      def apply(from: CaseInsensitiveString) = newBuilder
      def apply() = newBuilder
    }

  def newBuilder: Builder[Char, CaseInsensitiveString] =
    StringBuilder.newBuilder.mapResult(CaseInsensitiveString(_))
}
