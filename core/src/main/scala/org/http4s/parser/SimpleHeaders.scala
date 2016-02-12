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


import headers._
import java.net.InetAddress
import org.http4s.headers.ETag.EntityTag
import org.http4s.util.CaseInsensitiveString._
import org.parboiled2.Rule1

import scalaz.NonEmptyList

/**
 * parser rules for all headers that can be parsed with one simple rule
 */
private[parser] trait SimpleHeaders {

  def ALLOW(value: String): ParseResult[Allow] = {
    new Http4sHeaderParser[Allow](value) {
      def entry = rule {
        oneOrMore(Token).separatedBy(ListSep) ~ EOL ~>  { ts: Seq[String] =>
          val ms = ts.map(Method.fromString(_).getOrElse(sys.error("Impossible. Please file a bug report.")))
          Allow(NonEmptyList(ms.head, ms.tail:_*))
        }
      }
    }.parse
  }

  def CONNECTION(value: String): ParseResult[Connection] = {
    new Http4sHeaderParser[Connection](value) {
      def entry = rule (
            oneOrMore(Token).separatedBy(ListSep) ~ EOL ~>
              {xs: Seq[String] => Connection(xs.head.ci, xs.tail.map(_.ci): _*)}
        )
    }.parse
  }

  def CONTENT_LENGTH(value: String) = new Http4sHeaderParser[`Content-Length`](value) {
    def entry = rule { Digits ~ EOL ~> {s: String => `Content-Length`(s.toLong)} }
  }.parse

  def CONTENT_ENCODING(value: String) = new Http4sHeaderParser[`Content-Encoding`](value) {
    def entry = rule { Token ~ EOL ~> {s: String =>
      `Content-Encoding`(ContentCoding.getOrElseCreate(s.ci))}
    }
  }.parse

  def CONTENT_DISPOSITION(value: String) = new Http4sHeaderParser[`Content-Disposition`](value) {
    def entry = rule {
     Token ~ zeroOrMore(";" ~ OptWS ~ Parameter) ~ EOL ~> { (token:String, params: Seq[(String, String)]) =>
      `Content-Disposition`(token, params.toMap)}
    }
  }.parse

  def DATE(value: String) = new Http4sHeaderParser[Date](value) {
    def entry = rule {
      HttpDate ~ EOL ~> (Date(_))
    }
  }.parse

//  // Do not accept scoped IPv6 addresses as they should not appear in the Host header,
//  // see also https://issues.apache.org/bugzilla/show_bug.cgi?id=35122 (WONTFIX in Apache 2 issue) and
//  // https://bugzilla.mozilla.org/show_bug.cgi?id=464162 (FIXED in mozilla)
  def HOST(value: String) = new Http4sHeaderParser[Host](value) {
    def entry = rule {
      (Token | IPv6Reference) ~ OptWS ~
        optional(":" ~ capture(oneOrMore(Digit)) ~> (_.toInt)) ~ EOL ~> (Host(_:String, _:Option[Int]))
    }
  }.parse

  def LAST_MODIFIED(value: String) = new Http4sHeaderParser[`Last-Modified`](value) {
    def entry = rule {
      HttpDate ~ EOL ~> (`Last-Modified`(_))
    }
  }.parse

  def IF_MODIFIED_SINCE(value: String) = new Http4sHeaderParser[`If-Modified-Since`](value) {
    def entry = rule {
      HttpDate ~ EOL ~> (`If-Modified-Since`(_))
    }
  }.parse

  def ETAG(value: String) = new Http4sHeaderParser[ETag](value) {
    def entry = rule { EntityTag ~> (ETag(_: ETag.EntityTag)) }
  }.parse

  def IF_NONE_MATCH(value: String) = new Http4sHeaderParser[`If-None-Match`](value) {
    def entry = rule {
      "*" ~ push(`If-None-Match`.`*`) |
      oneOrMore(EntityTag).separatedBy(ListSep) ~> { tags: Seq[EntityTag] =>
        `If-None-Match`(Some(NonEmptyList(tags.head, tags.tail:_*)))
      }
    }
  }.parse

  def TRANSFER_ENCODING(value: String) = new Http4sHeaderParser[`Transfer-Encoding`](value) {
    def entry = rule {
      oneOrMore(Token).separatedBy(ListSep) ~> { vals: Seq[String] =>
        if (vals.tail.isEmpty) `Transfer-Encoding`(TransferCoding.fromKey(vals.head.ci))
        else `Transfer-Encoding`(TransferCoding.fromKey(vals.head.ci), vals.tail.map(s => TransferCoding.fromKey(s.ci)): _*)
      }
    }
  }.parse

  def USER_AGENT(value: String) = new Http4sHeaderParser[`User-Agent`](value) {
    def entry = rule {
      product ~ zeroOrMore(RWS ~ (product | comment)) ~> (`User-Agent`(_,_))
    }

    def product: Rule1[AgentProduct] = rule {
      Token ~ optional("/" ~ Token) ~> (AgentProduct(_,_))
    }

    def comment: Rule1[AgentComment] = rule {
      capture(Comment) ~> { s: String => AgentComment(s.substring(1, s.length-1)) }
    }

    def RWS = rule { oneOrMore(anyOf(" \t")) }
  }.parse

  def X_FORWARDED_FOR(value: String) = new Http4sHeaderParser[`X-Forwarded-For`](value) {
    def entry = rule {
      oneOrMore((Ip ~> (Some(_)))  | ("unknown" ~ push(None))).separatedBy(ListSep) ~
        EOL ~> { xs: Seq[Option[InetAddress]] =>
        `X-Forwarded-For`(xs.head, xs.tail: _*)
      }
    }
  }.parse

}
