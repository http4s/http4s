/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
