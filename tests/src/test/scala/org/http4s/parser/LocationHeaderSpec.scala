package org.http4s
package parser

import headers.Location

import scalaz.\/-

// TODO: this could use more tests
class LocationHeaderSpec extends Http4sSpec {

  "LocationHeader parser" can {
    "Parse a simple uri" in {
      val s = fv"http://www.foo.com"
      val \/-(uri) = Uri.fromString(s.toString)
      val hs = Headers(Header(fn"Location", s))

      hs.get(Location) must beSome(Location(uri))
    }

    "Parse a simple uri with a path but no authority" in {
      val s = fv"http:/foo/bar"
      val \/-(uri) = Uri.fromString(s.toString)
      val hs = Headers(Header(fn"Location", s))

      hs.get(Location) must beSome(Location(uri))
    }

    "Parse a relative reference" in {
      val s = fv"/cats"
      val \/-(uri) = Uri.fromString(s.toString)
      val hs = Headers(Header(fn"Location", s))

      hs.get(Location) must beSome(Location(uri))
    }
  }
}
