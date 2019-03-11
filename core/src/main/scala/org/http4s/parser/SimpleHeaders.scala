/*
 * Derived from https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/parser/SimpleHeaders.scala
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

import cats.data.NonEmptyList
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import org.http4s.headers._
import org.http4s.headers.ETag.EntityTag
import org.http4s.internal.parboiled2.Rule1
import org.http4s.syntax.string._

/**
  * parser rules for all headers that can be parsed with one simple rule
  */
private[parser] trait SimpleHeaders {

  def ALLOW(value: String): ParseResult[Allow] =
    new Http4sHeaderParser[Allow](value) {
      def entry = rule {
        oneOrMore(Token).separatedBy(ListSep) ~ EOL ~> { ts: Seq[String] =>
          val ms = ts.map(
            Method
              .fromString(_)
              .right
              .toOption
              .getOrElse(sys.error("Impossible. Please file a bug report.")))
          Allow(NonEmptyList.of(ms.head, ms.tail: _*))
        }
      }
    }.parse

  def CONNECTION(value: String): ParseResult[Connection] =
    new Http4sHeaderParser[Connection](value) {
      def entry = rule(
        oneOrMore(Token).separatedBy(ListSep) ~ EOL ~> { xs: Seq[String] =>
          Connection(xs.head.ci, xs.tail.map(_.ci): _*)
        }
      )
    }.parse

  def CONTENT_LENGTH(value: String): ParseResult[`Content-Length`] =
    new Http4sHeaderParser[`Content-Length`](value) {
      def entry = rule {
        Digits ~ EOL ~> { s: String =>
          `Content-Length`.unsafeFromLong(s.toLong)
        }
      }
    }.parse

  def CONTENT_ENCODING(value: String): ParseResult[`Content-Encoding`] =
    new Http4sHeaderParser[`Content-Encoding`](value)
    with org.http4s.ContentCoding.ContentCodingParser {
      def entry = rule {
        EncodingRangeDecl ~ EOL ~> { c: ContentCoding =>
          `Content-Encoding`(c)
        }
      }
    }.parse

  def CONTENT_DISPOSITION(value: String): ParseResult[`Content-Disposition`] =
    new Http4sHeaderParser[`Content-Disposition`](value) {
      def entry = rule {
        Token ~ zeroOrMore(";" ~ OptWS ~ Parameter) ~ EOL ~> {
          (token: String, params: Seq[(String, String)]) =>
            `Content-Disposition`(token, params.toMap)
        }
      }
    }.parse

  def DATE(value: String): ParseResult[Date] =
    new Http4sHeaderParser[Date](value) {
      def entry = rule {
        HttpDate ~ EOL ~> (Date(_))
      }
    }.parse

  def EXPIRES(value: String): ParseResult[Expires] =
    new Http4sHeaderParser[Expires](value) {
      def entry = rule {
        HttpDate ~ EOL ~> (Expires(_)) | // Valid Expires header
          Digit1 ~ EOL ~> ((t: Int) =>
            Expires(org.http4s.HttpDate.unsafeFromEpochSecond(t.toLong / 1000L))) | // Used for bogus http servers returning 0
          NegDigit1 ~ EOL ~> Function
            .const(Expires(org.http4s.HttpDate.Epoch)) _ // Used for bogus http servers returning -1
      }
    }.parse

  def RETRY_AFTER(value: String): ParseResult[`Retry-After`] =
    new Http4sHeaderParser[`Retry-After`](value) {
      def entry = rule {
        HttpDate ~ EOL ~> ((t: org.http4s.HttpDate) => `Retry-After`(t)) | // Date value
          Digits ~ EOL ~> ((t: String) => `Retry-After`.unsafeFromLong(t.toLong))
      }
    }.parse

  def AGE(value: String): ParseResult[Age] =
    new Http4sHeaderParser[Age](value) {
      def entry = rule {
        Digits ~ EOL ~> ((t: String) => Age.unsafeFromLong(t.toLong))
      }
    }.parse

//  // Do not accept scoped IPv6 addresses as they should not appear in the Host header,
//  // see also https://issues.apache.org/bugzilla/show_bug.cgi?id=35122 (WONTFIX in Apache 2 issue) and
//  // https://bugzilla.mozilla.org/show_bug.cgi?id=464162 (FIXED in mozilla)
  def HOST(value: String): ParseResult[Host] =
    new Http4sHeaderParser[Host](value) with Rfc3986Parser {
      def charset = StandardCharsets.UTF_8

      def entry = rule {
        (Token | IpLiteral) ~ OptWS ~
          optional(":" ~ capture(oneOrMore(Digit)) ~> (_.toInt)) ~ EOL ~> (org.http4s.headers
          .Host(_: String, _: Option[Int]))
      }
    }.parse

  def LAST_EVENT_ID(value: String): ParseResult[`Last-Event-Id`] =
    new Http4sHeaderParser[`Last-Event-Id`](value) {
      def entry = rule {
        capture(zeroOrMore(ANY)) ~ EOL ~> { id: String =>
          `Last-Event-Id`(ServerSentEvent.EventId(id))
        }
      }
    }.parse

  def LAST_MODIFIED(value: String): ParseResult[`Last-Modified`] =
    new Http4sHeaderParser[`Last-Modified`](value) {
      def entry = rule {
        HttpDate ~ EOL ~> (`Last-Modified`(_))
      }
    }.parse

  def IF_MODIFIED_SINCE(value: String): ParseResult[`If-Modified-Since`] =
    new Http4sHeaderParser[`If-Modified-Since`](value) {
      def entry = rule {
        HttpDate ~ EOL ~> (`If-Modified-Since`(_))
      }
    }.parse

  def IF_UNMODIFIED_SINCE(value: String): ParseResult[`If-Unmodified-Since`] =
    new Http4sHeaderParser[`If-Unmodified-Since`](value) {
      def entry = rule {
        HttpDate ~ EOL ~> (`If-Unmodified-Since`(_))
      }
    }.parse

  def ETAG(value: String): ParseResult[ETag] =
    new Http4sHeaderParser[ETag](value) {
      def entry = rule { EntityTag ~> (ETag(_: ETag.EntityTag)) }
    }.parse

  def IF_MATCH(value: String): ParseResult[`If-Match`] =
    new Http4sHeaderParser[`If-Match`](value) {
      def entry = rule {
        "*" ~ push(`If-Match`.`*`) |
          oneOrMore(EntityTag).separatedBy(ListSep) ~> { tags: Seq[EntityTag] =>
            `If-Match`(Some(NonEmptyList.of(tags.head, tags.tail: _*)))
          }
      }
    }.parse

  def IF_NONE_MATCH(value: String): ParseResult[`If-None-Match`] =
    new Http4sHeaderParser[`If-None-Match`](value) {
      def entry = rule {
        "*" ~ push(`If-None-Match`.`*`) |
          oneOrMore(EntityTag).separatedBy(ListSep) ~> { tags: Seq[EntityTag] =>
            `If-None-Match`(Some(NonEmptyList.of(tags.head, tags.tail: _*)))
          }
      }
    }.parse

  def TRANSFER_ENCODING(value: String): ParseResult[`Transfer-Encoding`] =
    TransferCoding.parseList(value).map(`Transfer-Encoding`.apply)

  def USER_AGENT(value: String): ParseResult[`User-Agent`] =
    new Http4sHeaderParser[`User-Agent`](value) {
      def entry = rule {
        product ~ zeroOrMore(RWS ~ (product | comment)) ~> (`User-Agent`(_, _))
      }

      def product: Rule1[AgentProduct] = rule {
        Token ~ optional("/" ~ Token) ~> (AgentProduct(_, _))
      }

      def comment: Rule1[AgentComment] = rule {
        capture(Comment) ~> { s: String =>
          AgentComment(s.substring(1, s.length - 1))
        }
      }

      def RWS = rule { oneOrMore(anyOf(" \t")) }
    }.parse

  def X_FORWARDED_FOR(value: String): ParseResult[`X-Forwarded-For`] =
    new Http4sHeaderParser[`X-Forwarded-For`](value) with IpParser {
      def entry = rule {
        oneOrMore(
          (capture(IpV4Address | IpV6Address) ~> { s: String =>
            Some(InetAddress.getByName(s))
          }) |
            ("unknown" ~ push(None))).separatedBy(ListSep) ~
          EOL ~> { xs: Seq[Option[InetAddress]] =>
          `X-Forwarded-For`(xs.head, xs.tail: _*)
        }
      }
    }.parse
}
