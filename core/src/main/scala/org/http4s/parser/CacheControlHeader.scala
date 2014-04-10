package org.http4s.parser

import org.parboiled2.{Rule1, ParserInput}
import org.http4s.Header.`Cache-Control`
import org.http4s.CacheDirective
import org.http4s.CacheDirective._
import org.http4s.util.string._
import scala.concurrent.duration._


/**
 * @author Bryce Anderson
 *         Created on 1/29/14
 */
private[parser] trait CacheControlHeader {

  def CACHE_CONTROL(value: String) = new CacheControlParser(value).parse

  private class CacheControlParser(input: ParserInput) extends Http4sHeaderParser[`Cache-Control`](input) {

    def entry: Rule1[`Cache-Control`] = rule {
      oneOrMore(CacheDirective).separatedBy(ListSep) ~ EOI ~> { xs: Seq[CacheDirective] =>
        `Cache-Control`(xs.head, xs.tail:_*)
      }
    }

    def CacheDirective: Rule1[CacheDirective] = rule {
     ("no-cache" ~ optional("=" ~ FieldNames)) ~> (fn => `no-cache`(fn.map(_.map(_.ci)).getOrElse(Nil))) |
      "no-store" ~ push(`no-store`) |
      "no-transform" ~ push(`no-transform`) |
      "max-age=" ~ DeltaSeconds ~> (s => `max-age`(s)) |
      "max-stale" ~ optional("=" ~ DeltaSeconds) ~> (s => `max-stale`(s)) |
      "min-fresh=" ~ DeltaSeconds ~> (s => `min-fresh`(s)) |
      "only-if-cached" ~ push(`only-if-cached`) |
      "public" ~ push(`public`) |
      "private" ~ optional("=" ~ FieldNames) ~> (fn => `private`(fn.map(_.map(_.ci)).getOrElse(Nil))) |
      "must-revalidate" ~ push(`must-revalidate`) |
      "proxy-revalidate" ~ push(`proxy-revalidate`) |
      "s-maxage=" ~ DeltaSeconds ~> (s => `s-maxage`(s)) |
      "stale-if-error=" ~ DeltaSeconds ~> (s => `stale-if-error`(s)) |
      "stale-while-revalidate=" ~ DeltaSeconds ~> (s => `stale-while-revalidate`(s)) |
      (Token ~ optional("=" ~ (Token | QuotedString)) ~> { (name: String, arg: Option[String]) => org.http4s.CacheDirective(name.ci, arg) })
    }

    def FieldNames: Rule1[Seq[String]] = rule { oneOrMore(QuotedString).separatedBy(ListSep) }
    def DeltaSeconds: Rule1[Duration] = rule { capture(oneOrMore(Digit)) ~> {s: String => s.toLong.seconds} }
  }

}
