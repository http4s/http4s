package org.http4s

import org.scalatest.{Matchers, WordSpec}

class ServerProtocolSpec extends WordSpec with Matchers {
  import ServerProtocol._

  "HTTP versions" should {
    "be registered if standard" in {
      ServerProtocol.apply("HTTP/1.1") should be theSameInstanceAs `HTTP/1.1`
    }

    "parse for future versions" in {
      ServerProtocol.apply("HTTP/1.3") should equal (HttpVersion(1, 3))
    }

    "render with protocol and version" in {
      `HTTP/1.0`.toString should equal ("HTTP/1.0")
    }
  }

  "INCLUDED" should {
    "be registered" in {
      ServerProtocol("INCLUDED") should be theSameInstanceAs INCLUDED
    }

    "render as 'INCLUDED'" in {
      INCLUDED.toString should equal ("INCLUDED")
    }
  }

  "Extension versions" should {
    "parse with a version" in {
      ServerProtocol.apply("FOO/2.10") should equal (ExtensionVersion("FOO".ci, Some(Version(2, 10))))
    }

    "parse without a version" in {
      ServerProtocol.apply("FOO") should equal (ExtensionVersion("FOO".ci, None))
    }

    "render with a version" in {
      ServerProtocol.apply("FOO/2.10").toString should equal ("FOO/2.10")
    }

    "render without a verison" in {
      ServerProtocol.apply("FOO").toString should equal ("FOO")
    }
  }
}
