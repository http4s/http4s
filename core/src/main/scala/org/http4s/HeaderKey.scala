/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import cats.data.NonEmptyList
import cats.implicits._
import org.typelevel.ci.CIString
import scala.annotation.tailrec
import scala.reflect.ClassTag

sealed trait HeaderKey {
  type HeaderT <: Header

  def name: CIString

  def matchHeader(header: Header): Option[HeaderT]
  final def unapply(header: Header): Option[HeaderT] = matchHeader(header)

  override def toString: String = s"HeaderKey($name)"

  def parse(s: String): ParseResult[HeaderT]
}

object HeaderKey {
  sealed trait Extractable extends HeaderKey {
    def from(headers: Headers): Option[HeaderT]
    final def unapply(headers: Headers): Option[HeaderT] = from(headers)
  }

  /**
    * Represents a Header that should not be repeated.
    */
  trait Singleton extends Extractable {
    final def from(headers: Headers): Option[HeaderT] =
      headers.collectFirst(Function.unlift(matchHeader))
  }

  /**
    * Represents a header key whose multiple headers can be combined by joining
    * their values with a comma.  See RFC 2616, Section 4.2.
    */
  trait Recurring extends Extractable {
    type HeaderT <: Header.Recurring
    type GetT = Option[HeaderT]

    def apply(values: NonEmptyList[HeaderT#Value]): HeaderT

    def apply(first: HeaderT#Value, more: HeaderT#Value*): HeaderT =
      apply(NonEmptyList(first, more.toList))

    def from(headers: Headers): Option[HeaderT] = {
      @tailrec def loop(
          hs: Headers,
          acc: NonEmptyList[HeaderT#Value]): NonEmptyList[HeaderT#Value] =
        if (hs.nonEmpty)
          matchHeader(hs.toList.head) match {
            case Some(header) =>
              loop(Headers(hs.toList.tail), acc.concatNel(header.values.widen[HeaderT#Value]))
            case None =>
              loop(Headers(hs.toList.tail), acc)
          }
        else acc
      @tailrec def start(hs: Headers): Option[HeaderT] =
        if (hs.nonEmpty)
          matchHeader(hs.toList.head) match {
            case Some(header) =>
              Some(apply(loop(Headers(hs.toList.tail), header.values.widen[HeaderT#Value])))
            case None => start(Headers(hs.toList.tail))
          }
        else None
      start(headers)
    }
  }

  private[http4s] abstract class Internal[T <: Header: ClassTag] extends HeaderKey {
    type HeaderT = T
    val name = CIString(
      getClass.getName
        .split("\\.")
        .last
        .replaceAll("\\$minus", "-")
        .split("\\$")
        .last
        .replace("\\$$", ""))
    private val runtimeClass = implicitly[ClassTag[HeaderT]].runtimeClass
    override def matchHeader(header: Header): Option[HeaderT] =
      header match {
        case h if runtimeClass.isInstance(h) =>
          Some(header.asInstanceOf[HeaderT])
        case Header.Raw(_, _) if name == header.name && runtimeClass.isInstance(header.parsed) =>
          Some(header.parsed.asInstanceOf[HeaderT])
        case _ =>
          None
      }
  }

  private[http4s] trait StringKey extends Singleton {
    type HeaderT = Header
    override def matchHeader(header: Header): Option[HeaderT] =
      if (header.name == name) Some(header)
      else None
  }

  private[http4s] trait Default extends Internal[Header] with StringKey {
    override type HeaderT = Header

    override def parse(s: String): ParseResult[Header] =
      ParseResult.success(Header.Raw(name, s))
  }
}
