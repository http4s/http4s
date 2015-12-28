package org.http4s.util

import org.specs2.{ScalaCheck, Specification}

class UrlFormCodecSpec extends Specification with ScalaCheck {
  def is = s2"""
    UrlFormCodec.encode should include & in multi-valued attributes ${
      UrlFormCodec.encode(Map("foo" -> Seq("a", "b"))) should_== "foo=a&foo=b"
    }
  """
}