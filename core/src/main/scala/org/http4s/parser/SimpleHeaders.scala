package org.http4s
package parser

import org.parboiled2._
import Header._
import scala.util.Try

/**
 * parser rules for all headers that can be parsed with one simple rule
 */
private[parser] trait SimpleHeaders { self: HttpParser =>

  def CONNECTION(value: String): HeaderValidation = {
    new Http4sHeaderParser[Connection](value) {
      def entry = rule (
            oneOrMore(Token).separatedBy(ListSep) ~ EOI ~>
              {xs: Seq[String] => Header.Connection(xs.head, xs.tail: _*)}
        )
    }.parse
  }

  def CONTENT_LENGTH(value: String) = new Http4sHeaderParser[`Content-Length`](value) {
    def entry = rule {
      Digits ~ EOI ~> {s: String => `Content-Length`(s.toInt)}
    }
  }.parse

  def CONTENT_DISPOSITION(value: String) = new Http4sHeaderParser[`Content-Disposition`](value) {
    def entry = rule {
     Token ~ zeroOrMore(";" ~ Parameter) ~ EOI ~> { (token:String, params: Seq[(String, String)]) =>
      `Content-Disposition`(token, params.toMap)}
    }
  }.parse

  def DATE(value: String) = new Http4sHeaderParser[Date](value) {
    def entry = rule {
      HttpDate ~ EOI ~> (Date(_))
    }
  }.parse
//
//  // Do not accept scoped IPv6 addresses as they should not appear in the Host header,
//  // see also https://issues.apache.org/bugzilla/show_bug.cgi?id=35122 (WONTFIX in Apache 2 issue) and
//  // https://bugzilla.mozilla.org/show_bug.cgi?id=464162 (FIXED in mozilla)
//  def HOST = rule {
//    (Token | IPv6Reference) ~ OptWS ~ optional(":" ~ oneOrMore(Digit) ~> (_.toInt)) ~ EOI ~~> (Host(_, _))
//  }
//
//  def LAST_MODIFIED = rule {
//    HttpDate ~ EOI ~~> (`Last-Modified`(_))
//  }
//
//  def IF_MODIFIED_SINCE = rule {
//    HttpDate ~ EOI ~~> (`If-Modified-Since`(_))
//  }
//
//  def ETAG = rule {
//    zeroOrMore(AlphaNum) ~> (s => ETag(s))
//  }
//
//  def IF_NONE_MATCH = rule {
//    zeroOrMore(AlphaNum) ~> (s => `If-None-Match`(s))
//  }
//
//  def X_FORWARDED_FOR = rule {
//    oneOrMore(Ip ~~> (Some(_)) | "unknown" ~ push(None), separator = ListSep) ~ EOI ~~> (xs => `X-Forwarded-For`(xs.head, xs.tail: _*))
//  }

}