package org.http4s.parser

import org.http4s.{Headers, Header, Http4sSpec, Uri}
import Header.Location

import scalaz.\/-

class LocationHeaderSpec extends Http4sSpec {

  "LocationHeader parser" can {
    "Parse a simple uri" in {
      val s = "http://www.foo.com"
      val \/-(uri) = Uri.fromString(s)
      val hs = Headers(Header("Location", s))

      hs.get(Location) must_== Some(Location(uri))
    }

  }

}
