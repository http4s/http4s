package org.http4s.server.staticcontent

import org.specs2.mutable.Specification

class PathNormalizerSpec extends Specification {

  "PathNormalizer.removeDotSegments" should {
    "remove dot segments correctly" in {
      val path1 = "/a/b/c/./../../g"

      PathNormalizer.removeDotSegments(path1) must_=== "/a/g"

      val path2 = "mid/content=5/../6"
      PathNormalizer.removeDotSegments(path2) must_=== "mid/6"
    }
  }

}
