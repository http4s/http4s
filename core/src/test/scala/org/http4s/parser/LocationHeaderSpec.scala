package org.http4s
package parser

import cats.data._
import org.http4s.headers.Location

// TODO: this could use more tests
class LocationHeaderSpec extends Http4sSpec {

  "LocationHeader parser" can {
    "Parse a simple uri" in {
      val s = "http://www.foo.com"
      val Xor.Right(uri) = Uri.fromString(s)
      val hs = Headers(Header("Location", s))

      hs.get(Location) must_== Some(Location(uri))
    }

    "Parse a simple uri with a path but no authority" in {
      val s = "http:/foo/bar"
      val Xor.Right(uri) = Uri.fromString(s)
      val hs = Headers(Header("Location", s))

      hs.get(Location) must_== Some(Location(uri))
    }

    "Parse a relative reference" in {
      val s = "/cats"
      val Xor.Right(uri) = Uri.fromString(s)
      val hs = Headers(Header("Location", s))

      hs.get(Location) must_== Some(Location(uri))
    }
  }
}
