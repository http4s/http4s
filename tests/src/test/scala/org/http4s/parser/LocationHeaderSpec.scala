package org.http4s
package parser

import org.http4s.headers.Location

// TODO: this could use more tests
class LocationHeaderSpec extends Http4sSpec {

  "LocationHeader parser".can {
    "Parse a simple uri" in {
      val s = "http://www.foo.com"
      val Right(uri) = Uri.fromString(s)
      val hs = Headers.of(Header("Location", s))

      hs.get(Location) must beSome(Location(uri))
    }

    "Parse a simple uri with a path but no authority" in {
      val s = "http:/foo/bar"
      val Right(uri) = Uri.fromString(s)
      val hs = Headers.of(Header("Location", s))

      hs.get(Location) must beSome(Location(uri))
    }

    "Parse a relative reference" in {
      val s = "/cats"
      val Right(uri) = Uri.fromString(s)
      val hs = Headers.of(Header("Location", s))

      hs.get(Location) must beSome(Location(uri))
    }
  }
}
