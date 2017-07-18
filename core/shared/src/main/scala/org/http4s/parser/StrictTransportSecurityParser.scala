package org.http4s
package parser

import org.http4s.headers.`Strict-Transport-Security`
import org.http4s.internal.parboiled2._
import org.http4s.internal.parboiled2.support.{::, HNil}

private[parser] trait StrictTransportSecurityHeader {
  def STRICT_TRANSPORT_SECURITY(value: String): ParseResult[`Strict-Transport-Security`] =
    StrictTransportSecurityParser(value).parse

  private case class StrictTransportSecurityParser(override val input: ParserInput)
      extends Http4sHeaderParser[`Strict-Transport-Security`](input) {
    def entry: Rule1[`Strict-Transport-Security`] = rule {
      maxAge ~ zeroOrMore(";" ~ OptWS ~ stsAttributes) ~ EOI
    }

    def maxAge: Rule1[`Strict-Transport-Security`] = rule {
      "max-age=" ~ Digits ~> { (age: String) =>
        `Strict-Transport-Security`
          .unsafeFromLong(maxAge = age.toLong, includeSubDomains = false, preload = false)
      }
    }

    def stsAttributes
      : Rule[`Strict-Transport-Security` :: HNil, `Strict-Transport-Security` :: HNil] = rule {
      "includeSubDomains" ~ MATCH ~> { (sts: `Strict-Transport-Security`) =>
        sts.withIncludeSubDomains(true)
      } |
        "preload" ~ MATCH ~> { (sts: `Strict-Transport-Security`) =>
          sts.withPreload(true)
        }
    }
  }
}
