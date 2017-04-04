package org.http4s
package parser

import headers.Location

import scalaz.\/-

// TODO: this could use more tests
class LocationHeaderSpec extends Http4sSpec {

  "LocationHeader parser" can {
    "Parse a simple uri" in {
      val s = "http://www.foo.com"
      val \/-(uri) = Uri.fromString(s)
      val hs = Headers(Header("Location", s))

      hs.get(Location) must beSome(Location(uri))
    }

    "Parse a simple uri with a path but no authority" in {
      val s = "http:/foo/bar"
      val \/-(uri) = Uri.fromString(s)
      val hs = Headers(Header("Location", s))

      hs.get(Location) must beSome(Location(uri))
    }

    "Parse a relative reference" in {
      val s = "/cats"
      val \/-(uri) = Uri.fromString(s)
      val hs = Headers(Header("Location", s))

      hs.get(Location) must beSome(Location(uri))
    }
  }
}
