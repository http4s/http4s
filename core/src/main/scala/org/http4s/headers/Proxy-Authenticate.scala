package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.parser.ChallengeRules
import org.parboiled2._

import scalaz.NonEmptyList

object `Proxy-Authenticate` extends HeaderKey.Internal[`Proxy-Authenticate`] with HeaderKey.Recurring {
  override protected def parseHeader(raw: Raw): Option[`Proxy-Authenticate`] =
    new ProxyAuthenticateParser(raw.value).parse.toOption

  private class ProxyAuthenticateParser(input: ParserInput) extends ChallengeRules[`Proxy-Authenticate`](input) {
    def entry: Rule1[`Proxy-Authenticate`] = rule {
      oneOrMore(ChallengeRule).separatedBy(ListSep) ~ EOI ~> { xs: Seq[Challenge] =>
        `Proxy-Authenticate`(xs.head, xs.tail: _*)
      }
    }
  }
}

final case class `Proxy-Authenticate`(values: NonEmptyList[Challenge]) extends Header.RecurringRenderable {
  override def key = `Proxy-Authenticate`
  type Value = Challenge
}

