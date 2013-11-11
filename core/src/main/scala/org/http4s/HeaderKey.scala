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
trait RecurringHeaderKey extends HeaderKey { self =>
  type HeaderT <: RecurringHeader
  type GetT = Option[HeaderT]
  def apply(values: NonEmptyList[HeaderT#Value]): HeaderT
  def apply(first: HeaderT#Value, more: HeaderT#Value*): HeaderT = apply(NonEmptyList.apply(first, more: _*))
  def from(headers: HeaderCollection): GetT = {
    @tailrec def loop(hs: HeaderCollection, acc: NonEmptyList[HeaderT#Value]): NonEmptyList[HeaderT#Value] =
      if (hs.nonEmpty) matchHeader(hs.head) match {
        case Some(header) => loop(hs.tail, acc append header.values)
        case None => loop(hs.tail, acc)
      }
      else acc
    @tailrec def start(hs: HeaderCollection): GetT =
      if (hs.nonEmpty) matchHeader(hs.head) match {
        case Some(header) => Some(apply(loop(hs.tail, header.values)))
        case None => start(hs.tail)
      }
      else None
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
