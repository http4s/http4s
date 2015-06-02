package org.http4s
package headers

import scalaz.\/-


// TODO: this could use more tests
class LocationSpec extends HeaderParserSpec(Location) {

  "LocationHeader parser" can {
    "Parse a simple uri" in {
      val s = "http://www.foo.com"
      val \/-(uri) = Uri.fromString(s)
      val hs = Headers(Header("Location", s))

      hs.get(Location) must_== Some(Location(uri))
    }

    "Parse a simple uri with a path but no authority" in {
      val s = "http:/foo/bar"
      val \/-(uri) = Uri.fromString(s)
      val hs = Headers(Header("Location", s))

      hs.get(Location) must_== Some(Location(uri))
    }

    "Fail without an absolute uri" in {  // an absolute uri needs a scheme
      val s = "www.foo.com"
      val \/-(uri) = Uri.fromString(s)
      val hs = Headers(Header("Location", s))

      hs.get(Location) must_== None
    }

  }

}
