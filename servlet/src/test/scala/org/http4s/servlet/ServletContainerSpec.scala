/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.servlet

import org.http4s.Http4sSpec

class ServletContainerSpec extends Http4sSpec {
  "prefixMapping" should {
    import ServletContainer.prefixMapping

    "append /* when prefix does not have trailing slash" in {
      prefixMapping("/foo") must_== "/foo/*"
    }

    "append * when prefix has trailing slash" in {
      prefixMapping("/") must_== "/*"
    }
  }
}
