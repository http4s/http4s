package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.parser.Http4sHeaderParser
import org.http4s.util.string._

import scalaz.NonEmptyList


object `Transfer-Encoding` extends HeaderKey.Internal[`Transfer-Encoding`] with HeaderKey.Recurring {
  override protected def parseHeader(raw: Raw): Option[`Transfer-Encoding`] = {
    new Http4sHeaderParser[`Transfer-Encoding`](raw.value) {
      def entry = rule {
        oneOrMore(Token).separatedBy(ListSep) ~> { vals: Seq[String] =>
          if (vals.tail.isEmpty) `Transfer-Encoding`(TransferCoding.fromKey(vals.head.ci))
          else `Transfer-Encoding`(TransferCoding.fromKey(vals.head.ci), vals.tail.map(s => TransferCoding.fromKey(s.ci)): _*)
        }
      }
    }.parse.toOption
  }
}

final case class `Transfer-Encoding`(values: NonEmptyList[TransferCoding]) extends Header.RecurringRenderable {
  override def key = `Transfer-Encoding`
  def hasChunked = values.list.exists(_.renderString.equalsIgnoreCase("chunked"))
  type Value = TransferCoding
}

