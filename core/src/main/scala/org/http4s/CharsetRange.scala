package org.http4s

import util._

import scalaz.{Equal, Show}

sealed abstract class CharsetRange extends HasQValue with Renderable {
  def qValue: QValue
  def withQValue(q: QValue): CharsetRange
  def isSatisfiedBy(charset: Charset): Boolean
}

object CharsetRange extends CharsetRangeInstances {
  sealed case class `*`(qValue: QValue) extends CharsetRange {
    final override def withQValue(q: QValue): CharsetRange.`*` = copy(qValue = q)
    final def isSatisfiedBy(charset: Charset): Boolean = qValue.isAcceptable
    final def render(writer: Writer): writer.type = writer << "*" << qValue
  }

  object `*` extends `*`(QValue.One)

  final case class Atom protected[http4s] (charset: Charset, qValue: QValue = QValue.One) extends CharsetRange {
    override def withQValue(q: QValue): CharsetRange.Atom = copy(qValue = q)
    def isSatisfiedBy(charset: Charset): Boolean = qValue.isAcceptable && this.charset == charset
    def render(writer: Writer): writer.type = writer << charset << qValue
  }

  implicit def fromCharset(cs: Charset): CharsetRange.Atom = cs.toRange
}

trait CharsetRangeInstances {
  implicit val CharacterSetEqual: Equal[CharsetRange] = Equal.equalA
  implicit val CharsetShow: Show[Charset] = Show.shows(_.toString)
}
