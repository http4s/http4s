/*
 * Derived from https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/parser/CacheControlHeader.scala
 *
 * Copyright (C) 2011-2012 spray.io
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
package parser

import java.util.concurrent.TimeUnit
import org.http4s.headers.`Cache-Control`
import org.http4s.internal.parboiled2.{ParserInput, Rule1}
import org.http4s.CacheDirective._
import org.http4s.syntax.string._
import scala.concurrent.duration._

private[parser] trait CacheControlHeader {
  def CACHE_CONTROL(value: String): ParseResult[`Cache-Control`] =
    new CacheControlParser(value).parse

  private class CacheControlParser(input: ParserInput)
      extends Http4sHeaderParser[`Cache-Control`](input) {
    def entry: Rule1[`Cache-Control`] =
      rule {
        oneOrMore(CacheDirective).separatedBy(ListSep) ~ EOI ~> { (xs: Seq[CacheDirective]) =>
          `Cache-Control`(xs.head, xs.tail: _*)
        }
      }

    def CacheDirective: Rule1[CacheDirective] =
      rule {
        ("no-cache" ~ optional("=" ~ FieldNames)) ~> (fn =>
          `no-cache`(fn.map(_.map(_.ci).toList).getOrElse(Nil))) |
          "no-store" ~ push(`no-store`) |
          "no-transform" ~ push(`no-transform`) |
          "max-age=" ~ DeltaSeconds ~> (s => `max-age`(s)) |
          "max-stale" ~ optional("=" ~ DeltaSeconds) ~> (s => `max-stale`(s)) |
          "min-fresh=" ~ DeltaSeconds ~> (s => `min-fresh`(s)) |
          "only-if-cached" ~ push(`only-if-cached`) |
          "public" ~ push(`public`) |
          "private" ~ optional("=" ~ FieldNames) ~> (fn =>
            `private`(fn.map(_.map(_.ci).toList).getOrElse(Nil))) |
          "must-revalidate" ~ push(`must-revalidate`) |
          "proxy-revalidate" ~ push(`proxy-revalidate`) |
          "s-maxage=" ~ DeltaSeconds ~> (s => `s-maxage`(s)) |
          "stale-if-error=" ~ DeltaSeconds ~> (s => `stale-if-error`(s)) |
          "stale-while-revalidate=" ~ DeltaSeconds ~> (s => `stale-while-revalidate`(s)) |
          (Token ~ optional("=" ~ (Token | QuotedString)) ~> {
            (name: String, arg: Option[String]) =>
              org.http4s.CacheDirective(name.ci, arg)
          })
      }

    def FieldNames: Rule1[collection.Seq[String]] =
      rule {
        oneOrMore(QuotedString).separatedBy(ListSep)
      }
    def DeltaSeconds: Rule1[Duration] =
      rule {
        capture(oneOrMore(Digit)) ~> { (s: String) =>
          Duration(s.toLong, TimeUnit.SECONDS)
        }
      }
  }
}
