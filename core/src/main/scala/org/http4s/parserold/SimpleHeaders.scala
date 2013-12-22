package org.http4s
package parserold

import org.parboiled.scala._
import BasicRules._
import Header._

/**
 * parser rules for all headers that can be parsed with one simple rule
 */
private[parserold] trait SimpleHeaders {
  this: Parser with ProtocolParameterRules with AdditionalRules =>

//  def CONNECTION = rule (
//    oneOrMore(Token, separator = ListSep) ~ EOI ~~> (xs => Header.Connection(xs.head, xs.tail: _*))
//  )
//
//  def CONTENT_LENGTH = rule {
//    oneOrMore(Digit) ~> (s => `Content-Length`(s.toInt)) ~ EOI
//  }
//
//  def CONTENT_DISPOSITION = rule {
//    Token ~ zeroOrMore(";" ~ Parameter) ~ EOI ~~> (_.toMap) ~~> (`Content-Disposition`(_, _))
//  }
//
//  def DATE = rule {
//    HttpDate ~ EOI ~~> (Date(_))
//  }

  // Do not accept scoped IPv6 addresses as they should not appear in the Host header,
  // see also https://issues.apache.org/bugzilla/show_bug.cgi?id=35122 (WONTFIX in Apache 2 issue) and
  // https://bugzilla.mozilla.org/show_bug.cgi?id=464162 (FIXED in mozilla)
  def HOST = rule {
    (Token | IPv6Reference) ~ OptWS ~ optional(":" ~ oneOrMore(Digit) ~> (_.toInt)) ~ EOI ~~> (Host(_, _))
  }

  def LAST_MODIFIED = rule {
    HttpDate ~ EOI ~~> (`Last-Modified`(_))
  }

  def IF_MODIFIED_SINCE = rule {
    HttpDate ~ EOI ~~> (`If-Modified-Since`(_))
  }

  def ETAG = rule {
    zeroOrMore(AlphaNum) ~> (s => ETag(s))
  }

  def IF_NONE_MATCH = rule {
    zeroOrMore(AlphaNum) ~> (s => `If-None-Match`(s))
  }

  def X_FORWARDED_FOR = rule {
    oneOrMore(Ip ~~> (Some(_)) | "unknown" ~ push(None), separator = ListSep) ~ EOI ~~> (xs => `X-Forwarded-For`(xs.head, xs.tail: _*))
  }

}