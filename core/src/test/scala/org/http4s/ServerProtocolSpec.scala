package org.http4s

import org.http4s.util.string._
import org.specs2.mutable.Specification

class ServerProtocolSpec extends Specification {
  import ServerProtocol._

  def resolve(str: String) = ServerProtocol.getOrElseCreate(str.ci)

  "HTTP versions" should {
    "be registered if standard" in {
      resolve("HTTP/1.1") must be (`HTTP/1.1`)
    }

    "parse for future versions" in {
      resolve("HTTP/1.3") must_==(HttpVersion(1, 3))
    }

    "render with protocol and version" in {
      `HTTP/1.0`.toString must_== ("HTTP/1.0")
    }
  }

  "INCLUDED" should {
    "be registered" in {
      resolve("INCLUDED") must be (INCLUDED)
    }

    "render as 'INCLUDED'" in {
      INCLUDED.toString must_== ("INCLUDED")
    }
  }

  "Extension versions" should {
    "parse with a version" in {
      resolve("FOO/2.10") must_== (ExtensionVersion("FOO".ci, Some(Version(2, 10))))
    }

    "parse without a version" in {
      resolve("FOO") must_== (ExtensionVersion("FOO".ci, None))
    }

    "render with a version" in {
      resolve("FOO/2.10").toString must_== ("FOO/2.10")
    }

    "render without a verison" in {
      resolve("FOO").toString must_== ("FOO")
    }
  }

  "HttpVersion extractor" should {
    "recognize HttpVersions" in {
      HttpVersion.unapply(`HTTP/1.1`: ServerProtocol) must_== (Some(1 -> 1))
    }

    "treat INCLUDED as HTTP/1.0" in {
      HttpVersion.unapply(INCLUDED) must_== (Some(1 -> 0))
    }

    "treat extension versions as not HTTP" in {
      HttpVersion.unapply(ExtensionVersion("Foo".ci, Some(Version(1, 1)))) should be (None)
    }
  }
}
