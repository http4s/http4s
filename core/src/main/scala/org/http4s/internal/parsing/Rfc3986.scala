/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.internal.parsing

import cats.parse.Parser.{char, charIn, string1}
import cats.parse.{Parser, Parser1}
import cats.parse.Rfc5234.{digit, hexdig}
import org.http4s.Uri.Ipv6Address

/** Common rules defined in Rfc3986
  *
  * @see [[https://tools.ietf.org/html/rfc3986]]
  */
private[http4s] object Rfc3986 {

  val ipv6: Parser1[Ipv6Address] = {

    def toIpv6(lefts: collection.Seq[Short], rights: collection.Seq[Short]): Ipv6Address =
      (lefts ++ collection.Seq.fill(8 - lefts.size - rights.size)(0.toShort) ++ rights) match {
        case collection.Seq(a, b, c, d, e, f, g, h) =>
          Ipv6Address(a, b, c, d, e, f, g, h)
      }

    val h16: Parser1[Short] = {
      (hexdig ~ hexdig.? ~ hexdig.? ~ hexdig.?).string.map { (s: String) => java.lang.Integer.parseInt(s, 16).toShort }
    }

    val decOctet = (char('1') ~ digit ~ digit)
      .orElse1(char('2') ~ charIn('0' to '4') ~ digit)
      .orElse1(string1("25") ~ charIn('0' to '5'))
      .orElse1(charIn('1' to '9') ~ digit)
      .orElse1(digit).string.map(_.toInt.toByte)

    val colon = char(':')

    val ls32: Parser1[(Short, Short)] = {
      val option1 = (h16 ~ colon.void ~ h16).map { case ((l, _), r) => (l, r) }
      val dot = char('.')
      val option2: Parser1[(Short, Short)] = (decOctet ~ dot ~ decOctet ~ dot ~ decOctet ~ dot ~ decOctet).map {
        case ((((((a, _), b), _), c), _), d) =>
          ((a << 8) | b).toShort -> ((c << 8) | d).toShort
      }
      option1.orElse1(option2)
    }

    val doubleColon = string1("::").void
    val h16Colon = h16 <* colon

    def repSix[A](p: Parser1[A], sep: Parser[Unit] = Parser.unit) = (p ~ sep ~ p ~ sep ~ p ~ sep ~ p ~ sep ~ p ~ sep ~ p)
      .map { case ((((((((((one, _), two), _), three), _), four), _), five), _), six) => List(one, two, three, four, five, six) }.backtrack
      .orElse1(repFive(p, sep))
    def repFive[A](p: Parser1[A], sep: Parser[Unit]) = (p ~ sep ~ p ~ sep ~ p ~ sep ~ p ~ sep ~ p)
      .map { case ((((((((one, _), two), _), three), _), four), _), five) => List(one, two, three, four, five) }.backtrack
      .orElse1(repFour(p, sep))
    def repFour[A](p: Parser1[A], sep: Parser[Unit]) = (p ~ sep ~ p ~ sep ~ p ~ sep ~ p)
      .map { case ((((((one, _), two), _), three), _), four) => List(one, two, three, four) }.backtrack
      .orElse1(repThree(p, sep))
    def repThree[A](p: Parser1[A], sep: Parser[Unit]) = (p ~ sep ~ p ~ sep ~ p)
      .map { case ((((one, _), two), _), three) => List(one, two, three) }.backtrack
      .orElse1(repTwo(p, sep))
    def repTwo[A](p: Parser1[A], sep: Parser[Unit]) = (p ~ sep ~ p)
      .map { case ((one, _), two) => List(one, two) }.backtrack
      .orElse1(repOne(p))
    def repOne[A](p: Parser1[A]) = p.map(List(_)).backtrack


    (repSix(h16Colon) ~ ls32)
      .map { case (ls: collection.Seq[Short], (r0: Short, r1: Short)) => toIpv6(ls, Seq(r0, r1)) }
      .orElse1((doubleColon *> repSix(h16, colon))
        .map { rs: collection.Seq[Short] => toIpv6(Seq.empty, rs) }).backtrack
      .orElse1((h16.?.with1 ~ doubleColon.void ~ repFive(h16, colon))
        .map { case ((ls: Option[Short], _), rs: List[Short]) => toIpv6(ls.toSeq, rs) }).backtrack
      .orElse1((repTwo(h16, colon.void).?.with1 ~ doubleColon.void ~ repFour(h16, colon))
        .map { case ((ls: Option[List[Short]], _), rs: List[Short]) => toIpv6(ls.getOrElse(Seq.empty), rs) }).backtrack
      .orElse1((repThree(h16, colon.void).?.with1 ~ doubleColon ~ repThree(h16, colon))
        .map { case ((ls: Option[List[Short]], _), rs: List[Short]) => toIpv6(ls.getOrElse(Seq.empty), rs) }).backtrack
      .orElse1((repFour(h16, colon.void).?.with1 ~ doubleColon ~ repTwo(h16, colon))
        .map { case ((ls: Option[collection.Seq[Short]], _), rs) => toIpv6(ls.getOrElse(Seq.empty), rs) }).backtrack
      .orElse1((repFive(h16, colon.void).?.with1 ~ doubleColon ~ h16)
        .map { case ((ls: Option[collection.Seq[Short]], _), rs: Short) => toIpv6(ls.getOrElse(Seq.empty), Seq(rs)) }).backtrack
      .orElse1((repSix(h16, colon.void).?.with1 ~ doubleColon)
        .map { case (ls: Option[collection.Seq[Short]], _) => toIpv6(ls.getOrElse(Seq.empty), Seq.empty) }).backtrack
  }

}
