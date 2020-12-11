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

import cats.effect.IO
import org.http4s.{Headers, Request, Uri}

class RefererSpec extends HeaderLaws {
  checkAll("Referer", headerLaws(`Retry-After`))

  def getUri(uri: String): Uri =
    Uri.fromString(uri).fold(_ => sys.error(s"Failure on uri: $uri"), identity)

  "render" should {
    "format an absolute url" in {
      Referer(getUri("http://localhost:8080")).renderString must_== "Referer: http://localhost:8080"
    }
    "format a relative url" in {
      Referer(getUri("../../index.html")).renderString must_== "Referer: ../../index.html"
    }
  }

  "parse" should {
    "accept absolute url" in {
      Referer.parse("http://localhost:8080").map(_.uri) must beRight(
        getUri("http://localhost:8080"))
    }
    "accept relative url" in {
      Referer.parse("../../index.html").map(_.uri) must beRight(getUri("../../index.html"))
    }
  }

  "should be extractable" in {
    val referer = Referer(getUri("http://localhost:8080"))
    val request = Request[IO](headers = Headers.of(referer))

    request.headers.get(Referer) should beSome(referer)
  }
}
