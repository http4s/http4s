package org.http4s.parser

import org.parboiled2.{Rule1, ParserInput}
import org.http4s.Header.`Cache-Control`
import org.http4s.CacheDirective
import org.http4s.CacheDirective._


/**
 * @author Bryce Anderson
 *         Created on 1/29/14
 */
trait CacheControlHeader {

  def CACHE_CONTROL(value: String) = new CacheControlParser(value).parse

  private class CacheControlParser(input: ParserInput) extends Http4sHeaderParser[`Cache-Control`](input) {

    def entry: Rule1[`Cache-Control`] = rule {
      oneOrMore(CacheDirective).separatedBy(ListSep) ~ EOI ~> { xs: Seq[CacheDirective] =>
        `Cache-Control`(xs.head, xs.tail:_*)
      }
    }

    def CacheDirective: Rule1[CacheDirective] = rule {
     ("no-cache" ~ optional("=" ~ FieldNames)) ~> (fn => `no-cache`(fn.getOrElse(Nil))) |
      "no-store" ~ push(`no-store`) |
      "no-transform" ~ push(`no-transform`) |
      "max-age=" ~ DeltaSeconds ~> (`max-age`(_)) |
      "max-stale" ~ optional("=" ~ DeltaSeconds) ~> (`max-stale`(_)) |
      "min-fresh=" ~ DeltaSeconds ~> (`min-fresh`(_)) |
      "only-if-cached" ~ push(`only-if-cached`) |
      "public" ~ push(`public`) |
      "private" ~ optional("=" ~ FieldNames) ~> (fn => `private`(fn.getOrElse(Nil))) |
      "must-revalidate" ~ push(`must-revalidate`) |
      "proxy-revalidate" ~ push(`proxy-revalidate`) |
      "s-maxage=" ~ DeltaSeconds ~> (`s-maxage`(_)) |
      (Token ~ optional("=" ~ (Token | QuotedString)) ~> (CustomCacheDirective(_, _)))
    }

    def FieldNames: Rule1[Seq[String]] = rule { oneOrMore(QuotedString).separatedBy(ListSep) }
    def DeltaSeconds: Rule1[Long] = rule { capture(oneOrMore(Digit)) ~> {s: String => s.toLong} }
  }

}
