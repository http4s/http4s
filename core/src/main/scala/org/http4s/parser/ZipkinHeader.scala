package org.http4s
package parser

import org.parboiled2._
import org.parboiled2.CharPredicate.HexDigit
import org.http4s.headers._

private[parser] trait ZipkinHeader {
  def X_B3_TRACEID(value: String): ParseResult[`X-B3-TraceId`] = new Http4sHeaderParser[`X-B3-TraceId`](value) {
    def entry = rule {
      capture(16.times(HexDigit)) ~!~
        EOL ~> {s: String => `X-B3-TraceId`(java.lang.Long.valueOf(s,16))}
    }
  }.parse

  def X_B3_SPANID(value: String): ParseResult[`X-B3-SpanId`] = new Http4sHeaderParser[`X-B3-SpanId`](value) {
    def entry = rule {
      capture(16.times(HexDigit)) ~!~
        EOL ~> {s: String => `X-B3-SpanId`(java.lang.Long.valueOf(s,16))}
    }
  }.parse

  def X_B3_PARENTSPANID(value: String): ParseResult[`X-B3-ParentSpanId`] = new Http4sHeaderParser[`X-B3-ParentSpanId`](value) {
    def entry = rule {
      capture(16.times(HexDigit)) ~!~
        EOL ~> {s: String => `X-B3-ParentSpanId`(java.lang.Long.valueOf(s,16))}
    }
  }.parse

  def X_B3_FLAGS(value: String): ParseResult[`X-B3-Flags`] = new Http4sHeaderParser[`X-B3-Flags`](value) {
    def entry = rule { Digits ~ EOL ~> {s: String => `X-B3-Flags`(getFlags(s.toLong))} }

    def getFlags(x: Long): List[`X-B3-Flags`.Flag] = {
      import `X-B3-Flags`.Flag
      def isDebug(x: Long): Option[Flag] =
        if ((x >> 0 & 1) == 1) Option(Flag.Debug) else None
      def isSamplingSet(x: Long): Option[Flag] =
        if ((x >> 1 & 1) == 1) Option(Flag.SamplingSet) else None
      def isSampled(x: Long): Option[Flag] =
        if ((x >> 2 & 1) == 1) Option(Flag.Sampled) else None

      List(isDebug(x), isSamplingSet(x), isSampled(x)).flatten
    }

  }.parse

  def X_B3_SAMPLED(value: String): ParseResult[`X-B3-Sampled`] = new Http4sHeaderParser[`X-B3-Sampled`](value) {
    def entry = rule {
      capture("0" | "1") ~!~
        EOL ~> {s: String => `X-B3-Sampled`(s=="1")}
    }
  }.parse
}
