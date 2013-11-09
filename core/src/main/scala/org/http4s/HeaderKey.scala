package org.http4s

import scalaz.NonEmptyList
import scala.annotation.tailrec

/**
 * @author Bryce Anderson
 *         Created on 11/3/13
 */
trait HeaderKey {
  type HeaderT <: Header
  type GetT

  def name: String

  def matchHeader(header: Header): Option[HeaderT]

  def unapply(header: Header): Option[HeaderT] = matchHeader(header)

  override def toString: String = s"HeaderKey(${name}})"

  def from(headers: HeaderCollection): GetT
}

trait GettableHeaderKey {
}

/**
 * Represents a Header that should not be repeated.
 */
trait SingletonHeaderKey extends HeaderKey {
  type GetT = Option[HeaderT]
  def from(headers: HeaderCollection): GetT = headers.collectFirst(Function.unlift(matchHeader))
}

/**
 * Represents a header key whose multiple headers can be combined by joining
 * their values with a comma.  See RFC 2616, Section 4.2.
 *
 * @tparam A The type of value contained by H
 */
trait RecurringHeaderKey extends HeaderKey {
  type Value
  type HeaderT <: RecurringHeader
  type GetT = Option[HeaderT]
  def apply(values: NonEmptyList[Value]): HeaderT
  def apply(first: Value, more: Value*): HeaderT = apply(NonEmptyList.apply(first, more: _*))
  def from(headers: HeaderCollection): GetT = {
    @tailrec def loop(hs: HeaderCollection, acc: NonEmptyList[Value]): NonEmptyList[Value] = hs match {
      case hs if hs.isEmpty => acc
      case hs if hs.head is this =>
        loop(hs.tail, acc append hs.head.asInstanceOf[HeaderT].values.asInstanceOf[NonEmptyList[Value]])
      case hs =>
        loop(hs.tail, acc)
    }
    @tailrec def start(hs: HeaderCollection): GetT = hs match {
      case hs if hs.isEmpty => None
      case hs if hs.head is this =>
        Some(apply(loop(hs.tail, hs.head.asInstanceOf[HeaderT].values.asInstanceOf[NonEmptyList[Value]])))
      case hs =>
        start(hs.tail)
    }
    start(headers)
  }
}

private[http4s] trait StringHeaderKey extends HeaderKey {
  type HeaderT = Header
  type GetT = Option[Header]

  override def matchHeader(header: Header): Option[HeaderT] = {
    if (header.name.equalsIgnoreCase(this.name)) Some(header)
    else None
  }

  def from(headers: HeaderCollection): GetT = headers.find(_ is this)
}

class SimpleHeaderKey(val name: String) extends StringHeaderKey
