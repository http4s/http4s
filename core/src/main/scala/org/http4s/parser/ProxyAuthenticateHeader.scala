package org.http4s.parser

import org.http4s.Challenge
import org.http4s.headers._
import org.parboiled2._

private[http4s] object ProxyAuthenticateHeader {

  def PROXY_AUTHENTICATE(value: String) = new ProxyAuthenticateParser(value).parse

  private class ProxyAuthenticateParser(input: ParserInput) extends ChallengeParser[`Proxy-Authenticate`](input) {
    def entry: Rule1[`Proxy-Authenticate`] = rule {
      oneOrMore(ChallengeRule).separatedBy(ListSep) ~ EOI ~> { xs: Seq[Challenge] =>
        `Proxy-Authenticate`(xs.head, xs.tail: _*)
      }
    }
  }

}
