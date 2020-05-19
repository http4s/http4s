package org.http4s
package parser

import org.http4s.headers.`Content-Base`

class ContentBaseHeaderSpec extends Http4sSpec {
  "ContentBaseHeader parser".can {
    "Parse a simple uri" in {
      val s = "http://www.foo.com"
      val Right(uri) = Uri.fromString(s)
      val hs = Headers.of(Header("Content-Base", s))

      hs.get(`Content-Base`) must beSome(`Content-Base`(uri))
    }

    "Parse a simple uri with a path but no authority" in {
      val s = "http:/foo/bar"
      val Right(uri) = Uri.fromString(s)
      val hs = Headers.of(Header("Content-Base", s))

      hs.get(`Content-Base`) must beSome(`Content-Base`(uri))
    }

    "Parse a relative reference" in {
      val s = "/cats"
      val Right(uri) = Uri.fromString(s)
      val hs = Headers.of(Header("Content-Base", s))

      hs.get(`Content-Base`) must beSome(`Content-Base`(uri))
    }
  }
}
