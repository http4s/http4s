package org.http4s
package parser

import org.scalatest.{Matchers, WordSpec}
import Header._
import scalaz.{Success}

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
  }

}
