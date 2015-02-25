package org.http4s.servlet

import org.http4s.Http4sSpec

class ServletContainerSpec extends Http4sSpec {
  "prefixMapping" should {
    import ServletContainer.prefixMapping

    "append /* when prefix does not have trailing slash" in {
      prefixMapping("/foo") must equal("/foo/*")
    }

    "append * when prefix has trailing slash" in {
      prefixMapping("/") must equal("/*")
    }
  }
}
