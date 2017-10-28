/*
 * Derived from https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/HttpEncoding.scala
 *
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team (http://github.com/jdegoes/blueeyes)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.http4s

import cats.{Order, Show}
import cats.data.NonEmptyList
import org.http4s.util._
import org.http4s.parser.{Http4sParser, Rfc2616BasicRules}

sealed abstract case class TransferCoding private (coding: String) extends Renderable {
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

  /**
    * Parse a list of Transfer Coding entries
    */
  def parse(s: String): ParseResult[NonEmptyList[TransferCoding]] =
    new Http4sParser[NonEmptyList[TransferCoding]](s, "Invalid Transfer Coding")
    with Rfc2616BasicRules {
      private def codingRule = rule {
        "chunked" ~ push(chunked) |
          "compress" ~ push(compress) |
          "deflate" ~ push(deflate) |
          "gzip" ~ push(gzip) |
          "identity" ~ push(identity)
      }

      def main = rule {
        oneOrMore(codingRule).separatedBy(ListSep) ~> { codes: Seq[TransferCoding] =>
          NonEmptyList.of(codes.head, codes.tail: _*)
        }
      }
    }.parse

  implicit val http4sInstancesForTransferCoding: Show[TransferCoding] with Order[TransferCoding] =
    new Show[TransferCoding] with Order[TransferCoding] {
      override def show(s: TransferCoding): String = s.coding

      override def compare(x: TransferCoding, y: TransferCoding): Int =
        x.coding.compareTo(y.coding)
    }

}
