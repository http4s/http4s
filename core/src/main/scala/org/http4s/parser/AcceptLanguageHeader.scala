package org.http4s.parser

import org.parboiled2._

import org.http4s.Header.`Accept-Language`
import org.http4s.{LanguageTag, Q}

/**
 * @author Bryce Anderson
 *         Created on 1/7/14
 */
private[parser] trait AcceptLanguageHeader {

  def ACCEPT_LANGUAGE(value: String) = new AcceptLanguageParser(value).parse

  private class AcceptLanguageParser(value: String)
    extends Http4sHeaderParser[`Accept-Language`](value) with MediaParser {
    def entry: Rule1[`Accept-Language`] = rule {
      oneOrMore(languageTag).separatedBy(ListSep) ~> { tags: Seq[LanguageTag] =>
        `Accept-Language`(tags.head, tags.tail:_*)
      }
    }

    def languageTag: Rule1[LanguageTag] = rule {
      capture(oneOrMore(Alpha)) ~ zeroOrMore("-" ~ Token) ~ TagQuality ~>
        { (main: String, sub: Seq[String], q: Q) => LanguageTag(main, q, sub) }
    }


    def TagQuality: Rule1[Q] = rule {
      (";" ~ OptWS ~ "q" ~ "=" ~ QValue) | push(Q.Unity)
    }

  }

}
