package org.http4s
package parser

import org.http4s.headers._
import org.http4s.internal.parboiled2._

trait ProxyAuthenticateHeader {
  def PROXY_AUTHENTICATE(value: String): ParseResult[`Proxy-Authenticate`] =
    new ProxyAuthenticateParser(value).parse

  private class ProxyAuthenticateParser(input: ParserInput)
      extends ChallengeParser[`Proxy-Authenticate`](input) {
    def entry: Rule1[`Proxy-Authenticate`] =
      rule {
        oneOrMore(ChallengeRule).separatedBy(ListSep) ~ EOI ~> { (xs: Seq[Challenge]) =>
          `Proxy-Authenticate`(xs.head, xs.tail: _*)
        }
      }
  }
}
