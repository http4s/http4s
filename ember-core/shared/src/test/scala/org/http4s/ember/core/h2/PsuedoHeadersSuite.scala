/*
 * Copyright 2019 http4s.org
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
package ember.core.h2

import cats.data.NonEmptyList
import org.http4s.headers._
import org.typelevel.ci._

class PsuedoHeadersSuite extends Http4sSuite {

  val connectionHeaders: List[Header.ToRaw] = List(
    Connection.close,
    `Transfer-Encoding`(NonEmptyList.of(TransferCoding.chunked)),
    Upgrade(NonEmptyList.of(Protocol(ci"name", None))),
    `Keep-Alive`.unsafeApply(Some(100L), None, List.empty),
    "Proxy-Connection" -> "keep-alive",
  )

  test("requestToHeaders should remove Connection Headers") {
    val request = Request[fs2.Pure]()
      .putHeaders(connectionHeaders: _*)

    val test = PseudoHeaders.requestToHeaders(request)
    val expected = NonEmptyList.of(
      (":method", "GET", false),
      (":scheme", "https", false),
      (":path", "/", false),
      (":authority", "", false),
    )

    assertEquals(test, expected)

  }
  test("responseToHeaders should remove Connection Headers") {
    val response = Response[fs2.Pure](Status.Ok)
      .putHeaders(connectionHeaders: _*)

    val test = PseudoHeaders.responseToHeaders(response)
    val expected = NonEmptyList.of(
      (":status", "200", false)
    )
    assertEquals(test, expected)
  }

  test("requestToHeaders should include query in :path") {
    val request = Request[fs2.Pure](uri = Uri().withQueryParam("q", "v") / "foo")

    val test = PseudoHeaders.requestToHeaders(request)
    val expected = NonEmptyList.of(
      (":method", "GET", false),
      (":scheme", "https", false),
      (":path", "/foo?q=v", false),
      (":authority", "", false),
    )

    assertEquals(test, expected)

  }

  test("requestToHeaders should include query in :path and treat missing path as /") {
    val request = Request[fs2.Pure](uri = Uri().withQueryParam("q", "v"))

    val test = PseudoHeaders.requestToHeaders(request)
    val expected = NonEmptyList.of(
      (":method", "GET", false),
      (":scheme", "https", false),
      (":path", "/?q=v", false),
      (":authority", "", false),
    )

    assertEquals(test, expected)

  }

  test("requestToHeaders should map OPTIONS requests without path to *") {
    val request = Request[fs2.Pure](method = Method.OPTIONS, uri = Uri())

    val test = PseudoHeaders.requestToHeaders(request)
    val expected = NonEmptyList.of(
      (":method", "OPTIONS", false),
      (":scheme", "https", false),
      (":path", "*", false),
      (":authority", "", false),
    )

    assertEquals(test, expected)

  }
}
