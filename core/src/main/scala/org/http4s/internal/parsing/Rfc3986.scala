/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.internal.parsing

import cats.parse.Parser.{char, charIn, string1}
import cats.parse.{Parser, Parser1}
import cats.parse.Rfc5234.{digit, hexdig}
import org.http4s.Uri.{Ipv4Address, Ipv6Address}
import cats.syntax.applicative._

/** Common rules defined in Rfc3986
  *
  * @see [[https://tools.ietf.org/html/rfc3986]]
  */
private[http4s] object Rfc3986 {

  private def ipv4Internal[A](f: (Byte, Byte, Byte, Byte) => A): Parser1[A] = {
    val decOctet = (char('1') ~ digit ~ digit).backtrack
      .orElse1(char('2') ~ charIn('0' to '4') ~ digit).backtrack
      .orElse1(string1("25") ~ charIn('0' to '5')).backtrack
      .orElse1(charIn('1' to '9') ~ digit).backtrack
      .orElse1(digit).string.map(_.toInt.toByte).backtrack

    val dot = char('.')
    val decOctDot = decOctet <* dot
    (decOctDot ~ decOctDot ~ decOctDot ~ decOctet).map {
      case (((a: Byte, b: Byte), c: Byte), d: Byte) => f(a, b, c, d)
    }
  }

  val ipv4: Parser1[Ipv4Address] = ipv4Internal(Ipv4Address.apply)

  val ipv6: Parser1[Ipv6Address] = {

    def toIpv6(lefts: collection.Seq[Short], rights: collection.Seq[Short]): Ipv6Address =
      lefts ++ collection.Seq.fill(8 - lefts.size - rights.size)(0.toShort) ++ rights match {
        case collection.Seq(a, b, c, d, e, f, g, h) =>
          Ipv6Address(a, b, c, d, e, f, g, h)
      }

    def repN[A](n: Int, p: Parser[A], sep: Parser[Unit] = Parser.unit): Parser[List[A]] = ((p ~ sep).replicateA(n - 1) ~ p)
      .map { case (head, tail) => head.map(_._1) :+ tail }.backtrack
      .orElse(if (n == 1) p.map(List(_)).backtrack else repN(n - 1, p, sep).backtrack)

    def repExactly[A](n: Int, p: Parser[A], sep: Parser[Unit] = Parser.unit): Parser[List[A]] = ((p ~ sep).replicateA(n - 1) ~ p)
      .map { case (head, tail) => head.map(_._1) :+ tail }.backtrack

    val h16: Parser1[Short] = {
      (hexdig ~ hexdig.? ~ hexdig.? ~ hexdig.?).string.map { (s: String) => java.lang.Integer.parseInt(s, 16).toShort }
    }

    val colon = char(':')
    val doubleColon = string1("::").void
    val h16Colon = h16 <* colon

    val ls32: Parser1[(Short, Short)] = {
      val option1 = (h16 <* colon.void) ~ h16
      val option2 = ipv4Internal { (a: Byte, b: Byte, c: Byte, d: Byte) =>
        ((a << 8) | b).toShort -> ((c << 8) | d).toShort
      }
      option1.backtrack.orElse1(option2)
    }

    (repN(6, h16Colon).with1 ~ ls32)
      .map { case (ls: collection.Seq[Short], (r0: Short, r1: Short)) => toIpv6(ls, Seq(r0, r1)) }.backtrack
      .orElse1((doubleColon *> repN(4, h16Colon, Parser.unit) ~ ls32)
        .map { case (rs: List[Short], (r0: Short, r1: Short)) => toIpv6(Seq.empty, rs :+ r0 :+ r1) }).backtrack
      .orElse1(((h16.?.with1 <* doubleColon) ~ repExactly(3, h16Colon) ~ ls32)
        .map { case ((ls: Option[Short], rs), (r4: Short, r5: Short)) => toIpv6(ls.toSeq, rs :+ r4 :+ r5) }).backtrack
      .orElse1(((repN(2, h16, colon.void).?.with1 <* doubleColon) ~ repExactly(2, h16Colon) ~ ls32)
        .map { case ((ls: Option[List[Short]], rs), (r2: Short, r3: Short)) => toIpv6(ls.getOrElse(Seq.empty), rs :+ r2 :+ r3) }).backtrack
      .orElse1(((repN(3, h16, colon.void).?.with1 <* doubleColon) ~ h16Colon ~ ls32)
        .map { case ((ls: Option[List[Short]], r0: Short), (r1: Short, r2: Short)) => toIpv6(ls.getOrElse(Seq.empty), Seq(r0, r1, r2)) }).backtrack
      .orElse1(((repN(4, h16, colon.void).?.with1 <* doubleColon) ~ ls32)
        .map { case (ls: Option[collection.Seq[Short]], (r0: Short, r1: Short)) => toIpv6(ls.getOrElse(Seq.empty), Seq(r0, r1)) }).backtrack
      .orElse1(((repN(5, h16, colon.void).?.with1 <* doubleColon) ~ h16)
        .map { case (ls: Option[collection.Seq[Short]], rs: Short) => toIpv6(ls.getOrElse(Seq.empty), Seq(rs)) }).backtrack
      .orElse1((repN(6, h16, colon.void).?.with1 <* doubleColon)
        .map { ls: Option[collection.Seq[Short]] => toIpv6(ls.getOrElse(Seq.empty), Seq.empty) }).backtrack
  }

}
