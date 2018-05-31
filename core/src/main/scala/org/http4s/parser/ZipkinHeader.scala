package org.http4s
package parser

import java.nio.ByteBuffer
import org.http4s.headers._
import org.http4s.internal.parboiled2.CharPredicate.HexDigit

private[parser] trait ZipkinHeader {
  def idStringToLong(idString: String) = {
    val bytes =
      idString
        .grouped(2)
        .toArray
        .map(x => Integer.parseUnsignedInt(x, 16).toByte)
    ByteBuffer.wrap(bytes).getLong
  }

  def X_B3_TRACEID(value: String): ParseResult[`X-B3-TraceId`] =
    new Http4sHeaderParser[`X-B3-TraceId`](value) {
      def entry = rule {
        capture(16.times(HexDigit)) ~
          optional(capture(16.times(HexDigit))) ~
          EOL ~> { (idMsb: String, idLsb: Option[String]) =>
          `X-B3-TraceId`(idStringToLong(idMsb), idLsb.map(idStringToLong))
        }
      }
    }.parse

  def X_B3_SPANID(value: String): ParseResult[`X-B3-SpanId`] =
    new Http4sHeaderParser[`X-B3-SpanId`](value) {
      def entry = rule {
        capture(16.times(HexDigit)) ~ EOL ~> { s: String =>
          `X-B3-SpanId`(idStringToLong(s))
        }
      }
    }.parse

  def X_B3_PARENTSPANID(value: String): ParseResult[`X-B3-ParentSpanId`] =
    new Http4sHeaderParser[`X-B3-ParentSpanId`](value) {
      def entry = rule {
        capture(16.times(HexDigit)) ~ EOL ~> { s: String =>
          `X-B3-ParentSpanId`(idStringToLong(s))
        }
      }
    }.parse

  def X_B3_FLAGS(value: String): ParseResult[`X-B3-Flags`] =
    new Http4sHeaderParser[`X-B3-Flags`](value) {
      def entry = rule {
        Digits ~ EOL ~> { s: String =>
          `X-B3-Flags`.fromLong(s.toLong)
        }
      }
    }.parse

  def X_B3_SAMPLED(value: String): ParseResult[`X-B3-Sampled`] =
    new Http4sHeaderParser[`X-B3-Sampled`](value) {
      def entry = rule {
        capture("0" | "1") ~!~
          EOL ~> { s: String =>
          `X-B3-Sampled`(s == "1")
        }
      }
    }.parse
}
