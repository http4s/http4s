package org.http4s

import util._

import scalaz.{Show, Order}

sealed abstract class CharsetRange extends QualityFactor with Renderable {
  def q: Q
  def withQuality(q: Q): CharsetRange
  def isSatisfiedBy(charset: Charset): Boolean
}

object CharsetRange extends CharsetRangeInstances {
  sealed case class `*`(q: Q) extends CharsetRange {
    final override def withQuality(q: Q): CharsetRange.`*` = copy(q = q)
    final def isSatisfiedBy(charset: Charset): Boolean = !q.unacceptable
    final def render[W <: Writer](writer: W): writer.type = writer ~ "*" ~ q
  }

  object `*` extends `*`(Q.Unity)

  final case class Atom protected[http4s] (charset: Charset, q: Q = Q.Unity) extends CharsetRange {
    override def withQuality(q: Q): CharsetRange.Atom = copy(q = q)
    def isSatisfiedBy(charset: Charset): Boolean = !q.unacceptable && this.charset == charset
    def render[W <: Writer](writer: W): writer.type = writer ~ charset ~ q
  }

  implicit def fromCharset(cs: Charset): CharsetRange.Atom = cs.toRange
}

trait CharsetRangeInstances {
  implicit val CharacterSetOrder: Order[CharsetRange] = Order[Q].reverseOrder.contramap(_.q)
  implicit val CharsetShow: Show[Charset] = Show.shows(_.toString)
}
