package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.parser.ChallengeRules
import org.parboiled2._

import scalaz.NonEmptyList

object `WWW-Authenticate` extends HeaderKey.Internal[`WWW-Authenticate`] with HeaderKey.Recurring {
  override protected def parseHeader(raw: Raw): Option[`WWW-Authenticate`] =
    new WWWAuthenticateParser(raw.value).parse.toOption

  private class WWWAuthenticateParser(input: ParserInput) extends ChallengeRules[`WWW-Authenticate`](input) {
    def entry: Rule1[`WWW-Authenticate`] = rule {
      oneOrMore(ChallengeRule).separatedBy(ListSep) ~ EOI ~> { xs: Seq[Challenge] =>
        `WWW-Authenticate`(xs.head, xs.tail: _*)
      }
    }
  }
}

final case class `WWW-Authenticate`(values: NonEmptyList[Challenge]) extends Header.RecurringRenderable {
  override def key = `WWW-Authenticate`
  type Value = Challenge
}

