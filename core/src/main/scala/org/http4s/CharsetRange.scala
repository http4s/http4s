package org.http4s

import cats.{Eq, Show}
import org.http4s.util._

sealed abstract class CharsetRange extends HasQValue with Renderable {
  def qValue: QValue
  def withQValue(q: QValue): CharsetRange

  @deprecated("Use `isSatisfiedBy` on the `Accept-Charset` header", "0.16.1")
  def isSatisfiedBy(charset: Charset): Boolean

  /** True if this charset range matches the charset.
    *
    * @since 0.16.1
    */
  final def matches(charset: Charset): Boolean = this match {
    case CharsetRange.`*`(_) => true
    case CharsetRange.Atom(cs, _) => charset == cs
  }
}

object CharsetRange {
  sealed case class `*`(qValue: QValue) extends CharsetRange {
    final override def withQValue(q: QValue): CharsetRange.`*` = copy(qValue = q)

    @deprecated("Use `Accept-Charset`.isSatisfiedBy(charset)", "0.16.1")
    final def isSatisfiedBy(charset: Charset): Boolean = qValue.isAcceptable

    final def render(writer: Writer): writer.type = writer << "*" << qValue
  }

  object `*` extends `*`(QValue.One)

  final case class Atom protected[http4s] (charset: Charset, qValue: QValue = QValue.One)
      extends CharsetRange {
    override def withQValue(q: QValue): CharsetRange.Atom = copy(qValue = q)

    @deprecated("Use `Accept-Charset`.isSatisfiedBy(charset)", "0.16.1")
    def isSatisfiedBy(charset: Charset): Boolean = qValue.isAcceptable && this.charset == charset

    def render(writer: Writer): writer.type = writer << charset << qValue
  }

  implicit def fromCharset(cs: Charset): CharsetRange.Atom = cs.toRange

  implicit val http4sEqForCharsetRange: Eq[CharsetRange] = Eq.fromUniversalEquals
  implicit val http4sShowForCharsetRange: Show[CharsetRange] = Show.fromToString
}
