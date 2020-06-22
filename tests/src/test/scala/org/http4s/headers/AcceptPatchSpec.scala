/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.headers

import cats.data.NonEmptyList
import org.http4s.MediaType

class AcceptPatchSpec extends HeaderLaws {
  checkAll("AcceptPatch", headerLaws(`Accept-Patch`))

  "render" should {
    "media types" in {
      `Accept-Patch`(
        NonEmptyList.of(
          new MediaType("text", "example"))).renderString must_== "Accept-Patch: text/example"
    }
    "mulitple media types" in {
      `Accept-Patch`(
        NonEmptyList.of(
          new MediaType("application", "example"),
          new MediaType(
            "text",
            "example",
            extensions =
              Map("charset" -> "utf-8")))).renderString must_== "Accept-Patch: application/example, text/example; charset=\"utf-8\""
    }
  }

}
