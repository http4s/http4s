package org.http4s

import scalaz.NonEmptyList

/**
 * @author Bryce Anderson
 *         Created on 11/3/13
 */


trait HeaderKey[T <: Header] {

  def name: String

  def matchHeader(header: Header): Option[T]

  def unapply(header: Header) = matchHeader(header)

  override def toString: String = "HeaderKey[" + name + "]"

  def unapply(headers: HeaderCollection): Option[T] =  {
    val it =headers.iterator
    while(it.hasNext) {
      val n = matchHeader(it.next())
      if (n.isDefined) return n
    }
    None // Didn't find it
  }

  def unapplySeq(headers: HeaderCollection): Option[Seq[T]] =
    Some(headers flatMap matchHeader)

  def from(headers: HeaderCollection): Option[T] = unapply(headers)

  def findIn(headers: HeaderCollection): Seq[T] = unapplySeq(headers) getOrElse Seq.empty

  def isNot(header: Header): Boolean = unapply(header).isEmpty

  def is(header: Header): Boolean = !isNot(header)
}

private[http4s] trait StringHeaderKey extends HeaderKey[Header] {
  override def matchHeader(header: Header): Option[Header] = {
    if (header.name.equalsIgnoreCase(this.name)) Some(header)
    else None
  }
}

class SimpleHeaderKey(val name: String) extends StringHeaderKey

/**
 * A companion object to a recurring header.
 *
 * @tparam A The type of value contained by H
 * @tparam H The type of header this is the companion to.
 */
trait RecurringHeaderKey[A, H <: RecurringHeader[A, H]] {
  def apply(values: NonEmptyList[A]): H
  def apply(first: A, more: A*): H = apply(NonEmptyList.apply(first, more: _*))
}
