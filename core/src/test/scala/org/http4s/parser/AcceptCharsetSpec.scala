package org.http4s.parser

import org.http4s.Header.`Accept-Charset`
import org.http4s.scalacheck.ScalazProperties
import org.http4s._
import org.scalacheck.Prop
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import scalaz.Validation
import org.http4s.Charset._
import org.http4s.CharsetRange._
import scalaz.syntax.id._
import scalaz.syntax.validation._

class AcceptCharsetSpec extends Specification with ScalaCheck with TestInstances with HeaderParserHelper[`Accept-Charset`] {

  def hparse(value: String): Validation[ParseErrorInfo, `Accept-Charset`] = HttpParser.ACCEPT_CHARSET(value)

  "Accept-Charset" should {
    "parse any list of CharsetRanges to itself" in {
      prop { h: `Accept-Charset` =>
        hparse(h.value) must_== h.success
      }
    }

    "is satisfied by a charset if the q value is > 0" in {
      prop { (h: `Accept-Charset`, cs: Charset) =>
        h.qValue(cs) > Q.fromString("0") ==> { h isSatisfiedBy cs }
      }
    }

    "is not satisfied by a charset if the q value is 0" in {
      prop { (h: `Accept-Charset`, cs: Charset) =>
        h.qValue(cs) == Q.fromString("0") ==> { !(h isSatisfiedBy cs) }
      }
    }
  }
}
