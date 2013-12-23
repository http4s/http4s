package org.http4s
package parser


import Header._
import java.net.InetAddress

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
    def entry = rule { Digits ~ EOI ~> {s: String => `Content-Length`(s.toInt)} }
  }.parse

  def CONTENT_DISPOSITION(value: String) = new Http4sHeaderParser[`Content-Disposition`](value) {
    def entry = rule {
     Token ~ zeroOrMore(";" ~ OptWS ~ Parameter) ~ EOI ~> { (token:String, params: Seq[(String, String)]) =>
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
  def HOST(value: String) = new Http4sHeaderParser[Host](value) {
    def entry = rule {
      (Token | IPv6Reference) ~ OptWS ~
        optional(":" ~ capture(oneOrMore(Digit)) ~> (_.toInt)) ~ EOI ~> (Host(_:String, _:Option[Int]))
    }
  }.parse

  def LAST_MODIFIED(value: String) = new Http4sHeaderParser[`Last-Modified`](value) {
    def entry = rule {
      HttpDate ~ EOI ~> (`Last-Modified`(_))
    }
  }.parse

  def IF_MODIFIED_SINCE(value: String) = new Http4sHeaderParser[`If-Modified-Since`](value) {
    def entry = rule {
      HttpDate ~ EOI ~> (`If-Modified-Since`(_))
    }
  }.parse

  def ETAG(value: String) = new Http4sHeaderParser[ETag](value) {
    def entry = rule {
      capture(zeroOrMore(AlphaNum)) ~> (ETag(_))
    }
  }.parse

  def IF_NONE_MATCH(value: String) = new Http4sHeaderParser[`If-None-Match`](value) {
    def entry = rule {
      capture(zeroOrMore(AlphaNum)) ~> (`If-None-Match`(_))
    }
  }.parse

  def X_FORWARDED_FOR(value: String) = new Http4sHeaderParser[`X-Forwarded-For`](value) {
    def entry = rule {
      oneOrMore((Ip ~> (Some(_)))  | ("unknown" ~ push(None))).separatedBy(ListSep) ~
        EOI ~> { xs: Seq[Option[InetAddress]] =>
        `X-Forwarded-For`(xs.head, xs.tail: _*)
      }
    }
  }.parse

}