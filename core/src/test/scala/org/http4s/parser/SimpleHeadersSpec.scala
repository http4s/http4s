package org.http4s
package parser

import org.scalatest.{Matchers, WordSpec}
import Header._
import scalaz.{Success}
import org.joda.time.DateTime

/**
 * @author Bryce Anderson
 *         Created on 12/22/13
 */
class SimpleHeadersSpec extends WordSpec with Matchers {

  "SimpleHeaders should parse" should {

    "Connection" in {
      val header = Connection("closed")
      HttpParser.parseHeader(header.raw) should equal(Success(header))
    }

    "Content-Length" in {
      val header = `Content-Length`(4)
      HttpParser.parseHeader(header.raw) should equal(Success(header))

      val bad = Header(header.name.toString, "foo")
      HttpParser.parseHeader(bad).isFailure should equal(true)
    }

    "Content-Disposition" in {
      val header = `Content-Disposition`("foo", Map("one" -> "two", "three" -> "four"))
      println(s"Header: $header")
      HttpParser.parseHeader(header.raw) should equal(Success(header))

      val bad = Header(header.name.toString, "foo; bar")
      HttpParser.parseHeader(bad).isFailure should equal(true)
    }

    "Date" in {       // mills are lost, get rid of them
      val header = Date(new DateTime().millisOfSecond().setCopy(0))
      HttpParser.parseHeader(header.raw) should equal(Success(header))

      val bad = Header(header.name.toString, "foo")
      HttpParser.parseHeader(bad).isFailure should equal(true)
    }
  }

}
