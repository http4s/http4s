/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/HttpEncoding.scala
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team
 */

package org.http4s

import cats.data.NonEmptyList
import cats.parse.Parser
import cats.{Order, Show}
import org.http4s.internal.hashLower
import org.http4s.internal.parsing.Rfc7230
import org.http4s.util._

class TransferCoding private (val coding: String) extends Ordered[TransferCoding] with Renderable {
  override def equals(o: Any) =
    o match {
      case that: TransferCoding => this.coding.equalsIgnoreCase(that.coding)
      case _ => false
    }

  private[this] var hash = 0
  override def hashCode(): Int = {
    if (hash == 0)
      hash = hashLower(coding)
    hash
  }

  override def toString = s"TransferCoding($coding)"

  override def compare(other: TransferCoding): Int =
    coding.compareToIgnoreCase(other.coding)

  override def render(writer: Writer): writer.type = writer.append(coding.toString)
}

object TransferCoding {
  private class TransferCodingImpl(coding: String) extends TransferCoding(coding)

  // https://www.iana.org/assignments/http-parameters/http-parameters.xml#transfer-coding
  val chunked: TransferCoding = new TransferCodingImpl("chunked")
  val compress: TransferCoding = new TransferCodingImpl("compress")
  val deflate: TransferCoding = new TransferCodingImpl("deflate")
  val gzip: TransferCoding = new TransferCodingImpl("gzip")
  val identity: TransferCoding = new TransferCodingImpl("identity")

  def parse(s: String): ParseResult[TransferCoding] =
    ParseResult.fromParser(parser, "Invalid transfer coding")(s)

  def parseList(s: String): ParseResult[NonEmptyList[TransferCoding]] =
    ParseResult.fromParser(Rfc7230.headerRep1(parser), "Invalid transfer coding list")(s)

  private[http4s] val parser: Parser[TransferCoding] = {
    import cats.parse.Parser.{ignoreCase, oneOf}
    oneOf(
      List(
        ignoreCase("chunked").as(chunked),
        ignoreCase("compress").as(compress),
        ignoreCase("deflate").as(deflate),
        ignoreCase("gzip").as(gzip),
        ignoreCase("identity").as(identity)
      ))
  }

  implicit val http4sOrderForTransferCoding: Order[TransferCoding] =
    Order.fromComparable
  implicit val http4sShowForTransferCoding: Show[TransferCoding] =
    Show.show(_.coding)
  implicit val http4sInstancesForTransferCoding: HttpCodec[TransferCoding] =
    new HttpCodec[TransferCoding] {
      override def parse(s: String): ParseResult[TransferCoding] =
        ParseResult.fromParser(TransferCoding.parser, "Invalid TransferCoding")(s)

      override def render(writer: Writer, coding: TransferCoding): writer.type =
        writer << coding.coding
    }
}
