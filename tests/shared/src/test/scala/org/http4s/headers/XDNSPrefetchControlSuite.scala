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

package org.http4s
package headers

import cats.effect.IO
import cats.syntax.either._

class XDNSPrefetchControlSuite extends HeaderLaws {

  test("render off") {
    assertEquals(`X-DNS-Prefetch-Control`.Off.renderString, "X-DNS-Prefetch-Control: off")
  }

  test("render on") {
    assertEquals(`X-DNS-Prefetch-Control`.On.renderString, "X-DNS-Prefetch-Control: on")
  }

  test("parsing off into X-DNS-Prefetch-Control-Off") {
    assertEquals(
      `X-DNS-Prefetch-Control`.parser.parseAll("off"),
      `X-DNS-Prefetch-Control`.Off.asRight,
    )
  }

  test("parsing on into X-DNS-Prefetch-Control-On") {
    assertEquals(
      `X-DNS-Prefetch-Control`.parser.parseAll("on"),
      `X-DNS-Prefetch-Control`.On.asRight,
    )
  }

  test("should be extractable off") {
    val xDnsPrefetchControl: `X-DNS-Prefetch-Control` = `X-DNS-Prefetch-Control`.Off
    val request = Request[IO](headers = Headers(xDnsPrefetchControl))

    val extracted = request.headers.get[`X-DNS-Prefetch-Control`]
    assertEquals(extracted, Some(xDnsPrefetchControl))
  }

  test("should be extractable on") {
    val xDnsPrefetchControl: `X-DNS-Prefetch-Control` = `X-DNS-Prefetch-Control`.On
    val request = Request[IO](headers = Headers(xDnsPrefetchControl))

    val extracted = request.headers.get[`X-DNS-Prefetch-Control`]
    assertEquals(extracted, Some(xDnsPrefetchControl))
  }
}
