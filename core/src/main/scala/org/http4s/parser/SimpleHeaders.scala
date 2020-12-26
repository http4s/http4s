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

import cats.syntax.all._
import cats.data.NonEmptyList
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import org.http4s.headers._
import org.http4s.headers.ETag.EntityTag
import org.http4s.internal.parboiled2.Rule1
import org.typelevel.ci.CIString

/** parser rules for all headers that can be parsed with one simple rule
  */
private[parser] trait SimpleHeaders {
  def ACCEPT_PATCH(value: String): ParseResult[`Accept-Patch`] =
    new Http4sHeaderParser[`Accept-Patch`](value) with MediaType.MediaTypeParser {
      def entry =
        rule {
          oneOrMore(MediaTypeFull).separatedBy(ListSep) ~ EOL ~> { (medias: Seq[MediaType]) =>
            `Accept-Patch`(NonEmptyList(medias.head, medias.tail.toList))
          }
        }
    }.parse

  def ACCESS_CONTROL_ALLOW_CREDENTIALS(
      value: String): ParseResult[`Access-Control-Allow-Credentials`] =
    new Http4sHeaderParser[`Access-Control-Allow-Credentials`](value) {
      def entry =
        rule {
          str("true") ~ EOL ~> { () =>
            `Access-Control-Allow-Credentials`()
          }
        }
    }.parse

  def ACCESS_CONTROL_ALLOW_HEADERS(value: String): ParseResult[`Access-Control-Allow-Headers`] =
    new Http4sHeaderParser[`Access-Control-Allow-Headers`](value) {
      def entry =
        rule {
          oneOrMore(Token).separatedBy(ListSep) ~ EOL ~> { (tokens: Seq[String]) =>
            `Access-Control-Allow-Headers`(
              NonEmptyList.of(CIString(tokens.head), tokens.tail.map(CIString(_)): _*)
            )
          }
        }
    }.parse

  def ACCESS_CONTROL_EXPOSE_HEADERS(value: String): ParseResult[`Access-Control-Expose-Headers`] =
    new Http4sHeaderParser[`Access-Control-Expose-Headers`](value) {
      def entry =
        rule {
          oneOrMore(Token).separatedBy(ListSep) ~ EOL ~> { (tokens: Seq[String]) =>
            `Access-Control-Expose-Headers`(
              NonEmptyList.of(CIString(tokens.head), tokens.tail.map(CIString(_)): _*)
            )
          }
        }
    }.parse

  def ALLOW(value: String): ParseResult[Allow] =
    new Http4sHeaderParser[Allow](value) {
      def entry =
        rule {
          zeroOrMore(Token).separatedBy(ListSep) ~ EOL ~> { (ts: Seq[String]) =>
            val ms = ts.map(
              Method
                .fromString(_)
                .toOption
                .getOrElse(sys.error("Impossible. Please file a bug report.")))
            Allow(ms.toSet)
          }
        }
    }.parse

  def CONNECTION(value: String): ParseResult[Connection] =
    new Http4sHeaderParser[Connection](value) {
      def entry =
        rule(
          oneOrMore(Token).separatedBy(ListSep) ~ EOL ~> { (xs: Seq[String]) =>
            Connection(CIString(xs.head), xs.tail.map(CIString(_)): _*)
          }
        )
    }.parse

  def CONTENT_LENGTH(value: String): ParseResult[`Content-Length`] =
    new Http4sHeaderParser[`Content-Length`](value) {
      def entry =
        rule {
          Digits ~ EOL ~> { (s: String) =>
            `Content-Length`.unsafeFromLong(s.toLong)
          }
        }
    }.parse

  def CONTENT_ENCODING(value: String): ParseResult[`Content-Encoding`] =
    (ContentCoding.parser <* AdditionalRules.EOL)
      .map(`Content-Encoding`(_))
      .parseAll(value)
      .leftMap(e => ParseFailure("Invalid header", e.toString))

  def CONTENT_DISPOSITION(value: String): ParseResult[`Content-Disposition`] =
    new Http4sHeaderParser[`Content-Disposition`](value) {
      def entry =
        rule {
          Token ~ zeroOrMore(";" ~ OptWS ~ Parameter) ~ EOL ~> {
            (token: String, params: collection.Seq[(String, String)]) =>
              `Content-Disposition`(token, params.toMap)
          }
        }
    }.parse

  def AGE(value: String): ParseResult[Age] =
    new Http4sHeaderParser[Age](value) {
      def entry =
        rule {
          Digits ~ EOL ~> ((t: String) => Age.unsafeFromLong(t.toLong))
        }
    }.parse

//  // Do not accept scoped IPv6 addresses as they should not appear in the Host header,
//  // see also https://issues.apache.org/bugzilla/show_bug.cgi?id=35122 (WONTFIX in Apache 2 issue) and
//  // https://bugzilla.mozilla.org/show_bug.cgi?id=464162 (FIXED in mozilla)
  def HOST(value: String): ParseResult[Host] =
    new Http4sHeaderParser[Host](value) with Rfc3986Parser {
      def charset = StandardCharsets.UTF_8

      def entry =
        rule {
          (Token | IpLiteral) ~ OptWS ~
            optional(":" ~ capture(oneOrMore(Digit)) ~> (_.toInt)) ~ EOL ~> (org.http4s.headers
              .Host(_: String, _: Option[Int]))
        }
    }.parse

  def LAST_EVENT_ID(value: String): ParseResult[`Last-Event-Id`] =
    new Http4sHeaderParser[`Last-Event-Id`](value) {
      def entry =
        rule {
          capture(zeroOrMore(ANY)) ~ EOL ~> { (id: String) =>
            `Last-Event-Id`(ServerSentEvent.EventId(id))
          }
        }
    }.parse

  def IF_MATCH(value: String): ParseResult[`If-Match`] =
    new Http4sHeaderParser[`If-Match`](value) {
      def entry =
        rule {
          "*" ~ push(`If-Match`.`*`) |
            oneOrMore(EntityTag).separatedBy(ListSep) ~> { (tags: Seq[EntityTag]) =>
              `If-Match`(Some(NonEmptyList.of(tags.head, tags.tail: _*)))
            }
        }
    }.parse

  def IF_NONE_MATCH(value: String): ParseResult[`If-None-Match`] =
    new Http4sHeaderParser[`If-None-Match`](value) {
      def entry =
        rule {
          "*" ~ push(`If-None-Match`.`*`) |
            oneOrMore(EntityTag).separatedBy(ListSep) ~> { (tags: Seq[EntityTag]) =>
              `If-None-Match`(Some(NonEmptyList.of(tags.head, tags.tail: _*)))
            }
        }
    }.parse

  def USER_AGENT(value: String): ParseResult[`User-Agent`] =
    AGENT_SERVER(value, `User-Agent`.apply)

  def SERVER(value: String): ParseResult[Server] =
    AGENT_SERVER(value, Server.apply)

  private def AGENT_SERVER[A <: Header](
      value: String,
      builder: (ProductId, List[ProductIdOrComment]) => A): ParseResult[A] =
    new Http4sHeaderParser[A](value) {
      def entry =
        rule {
          (productId ~ zeroOrMore(RWS ~ (product | comment))) ~> {
            (product: ProductId, tokens: collection.Seq[ProductIdOrComment]) =>
              builder(product, tokens.toList)
          }
        }

      def productId: Rule1[ProductId] =
        rule {
          (Token ~ optional("/" ~ Token)) ~> { (a: String, b: Option[String]) =>
            ProductId(a, b)
          }
        }

      def product: Rule1[ProductIdOrComment] = productId

      def comment: Rule1[ProductIdOrComment] =
        rule {
          capture(Comment) ~> { (s: String) =>
            ProductComment(s.substring(1, s.length - 1))
          }
        }
      def RWS = rule(oneOrMore(anyOf(" \t")))
    }.parse

  def X_FORWARDED_FOR(value: String): ParseResult[`X-Forwarded-For`] =
    new Http4sHeaderParser[`X-Forwarded-For`](value) with IpParser {
      def entry =
        rule {
          oneOrMore(
            (capture(IpV4Address | IpV6Address) ~> { (s: String) =>
              Some(InetAddress.getByName(s))
            }) |
              ("unknown" ~ push(None))).separatedBy(ListSep) ~
            EOL ~> { (xs: Seq[Option[InetAddress]]) =>
              `X-Forwarded-For`(xs.head, xs.tail: _*)
            }
        }
    }.parse
}
