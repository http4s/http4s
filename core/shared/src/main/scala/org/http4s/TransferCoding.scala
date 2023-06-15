/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

import cats.Order
import cats.Show
import cats.data.NonEmptyList
import cats.parse.Parser
import cats.syntax.all._
import org.http4s.internal.parsing.CommonRules
import org.http4s.util._
import org.typelevel.ci._

final case class TransferCoding private (coding: CIString)
    extends Ordered[TransferCoding]
    with Renderable {
  override def toString: String = s"TransferCoding($coding)"

  override def compare(other: TransferCoding): Int =
    coding.compare(other.coding)

  override def render(writer: Writer): writer.type = writer.append(coding.toString)
}

object TransferCoding {
  // https://www.iana.org/assignments/http-parameters/http-parameters.xml#transfer-coding
  val chunked: TransferCoding = TransferCoding(ci"chunked")
  val compress: TransferCoding = TransferCoding(ci"compress")
  val deflate: TransferCoding = TransferCoding(ci"deflate")
  val gzip: TransferCoding = TransferCoding(ci"gzip")
  val identity: TransferCoding = TransferCoding(ci"identity")

  def parse(s: String): ParseResult[TransferCoding] =
    ParseResult.fromParser(parser, "Invalid transfer coding")(s)

  def parseList(s: String): ParseResult[NonEmptyList[TransferCoding]] =
    ParseResult.fromParser(CommonRules.headerRep1(parser), "Invalid transfer coding list")(s)

  private[http4s] val parser: Parser[TransferCoding] = {
    import cats.parse.Parser.{ignoreCase, oneOf}
    oneOf(
      List(
        ignoreCase("chunked").as(chunked),
        ignoreCase("compress").as(compress),
        ignoreCase("deflate").as(deflate),
        ignoreCase("gzip").as(gzip),
        ignoreCase("identity").as(identity),
      )
    )
  }

  implicit val http4sOrderForTransferCoding: Order[TransferCoding] =
    Order.fromComparable
  implicit val http4sShowForTransferCoding: Show[TransferCoding] =
    Show[CIString].contramap(_.coding)
  implicit val http4sInstancesForTransferCoding: HttpCodec[TransferCoding] =
    new HttpCodec[TransferCoding] {
      override def parse(s: String): ParseResult[TransferCoding] =
        ParseResult.fromParser(TransferCoding.parser, "Invalid TransferCoding")(s)

      override def render(writer: Writer, coding: TransferCoding): writer.type =
        writer << coding.coding
    }
}
