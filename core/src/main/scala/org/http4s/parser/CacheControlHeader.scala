package org.http4s
package parser

import org.parboiled.scala._
import BasicRules._
import CacheDirectives._

private[parser] trait CacheControlHeader {
  this: Parser with ProtocolParameterRules =>

  def CACHE_CONTROL = rule (
    zeroOrMore(CacheDirective, separator = ListSep) ~ EOI ~~> (HttpHeaders.CacheControl(_))
  )

  def CacheDirective = rule (
      "no-cache" ~ push(`no-cache`)
    | "no-store" ~ push(`no-store`)
    | "no-transform" ~ push(`no-transform`)
    | "max-age=" ~ DeltaSeconds ~~> (`max-age`(_))

    | "max-stale" ~ optional("=" ~ DeltaSeconds) ~~> (`max-stale`(_))
    | "min-fresh=" ~ DeltaSeconds ~~> (`min-fresh`(_))
    | "only-if-cached" ~ push(`only-if-cached`)

    | "public" ~ push(`public`)
    | "private" ~ optional("=" ~ FieldNames) ~~> (fn => `private`(fn.getOrElse(Nil)))
    | "no-cache" ~ optional("=" ~ FieldNames) ~~> (fn => `no-cache`(fn.getOrElse(Nil)))
    | "must-revalidate" ~ push(`must-revalidate`)
    | "proxy-revalidate" ~ push(`proxy-revalidate`)
    | "s-maxage=" ~ DeltaSeconds ~~> (`s-maxage`(_))

    | Token ~ optional("=" ~ (Token | QuotedString)) ~~> (CustomCacheDirective(_, _))
  )

  def FieldNames = rule { oneOrMore(QuotedString, separator = ListSep) }
}