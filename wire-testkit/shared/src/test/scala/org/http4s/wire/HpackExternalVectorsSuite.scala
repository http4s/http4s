/*
 * Copyright 2026 http4s.org
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

package org.http4s.wire

import cats.effect.IO
import cats.syntax.all._
import scodec.bits.ByteVector

/** External decoder vectors, independent of this implementation and the RFC example sequences.
  *
  * Source: https://github.com/http2jp/hpack-test-case/tree/8a1406e7d14bfcb6c046021f13cc15cfb162726d
  * nghttp2-change-table-size/story_02.json: all ten cases, in their shared compression context.
  * go-hpack/story_02.json: cases zero and one, encoded by a second implementation.
  * Wire bytes and ordered expected headers are copied without alteration. The nghttp2 story
  * changes the encoded table capacity to 1365 and then 2730; both are below our advertised 4096.
  * The corpus includes historical HTTP fields: these tests validate HPACK octets, not HTTP semantics.
  * Fixtures are embedded so the identical data runs on JVM, JavaScript, and Native.
  */
class HpackExternalVectorsSuite extends RuntimeDiagnostics.Suite {
  private def verify(fixtures: Vector[(String, Vector[(String, String)])]): IO[Unit] =
    HpackWire.decoder[IO]().flatMap { decoder =>
      fixtures.zipWithIndex.traverse_ { case ((wire, expected), index) =>
        decoder
          .decode(ByteVector.fromValidHex(wire))
          .map(actual => assertEquals(actual, expected, s"external fixture sequence $index"))
      }
    }

  test("external nghttp2 Huffman, dynamic references, and table-size changes") {
    verify(nghttp2Story02)
  }

  test("external Go HPACK Huffman literals") {
    verify(goStory02)
  }

  /*
   * Embedded fixture data is distributed under its original MIT license:
   * https://github.com/http2jp/hpack-test-case/blob/8a1406e7d14bfcb6c046021f13cc15cfb162726d/LICENSE
   *
   * Copyright (c) 2013 HTTP/2 Japan Community
   *
   * Permission is hereby granted, free of charge, to any person obtaining
   * a copy of this software and associated documentation files (the
   * "Software"), to deal in the Software without restriction, including
   * without limitation the rights to use, copy, modify, merge, publish,
   * distribute, sublicense, and/or sell copies of the Software, and to
   * permit persons to whom the Software is furnished to do so, subject to
   * the following conditions:
   *
   * The above copyright notice and this permission notice shall be
   * included in all copies or substantial portions of the Software.
   *
   * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
   * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
   * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
   * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
   * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
   * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
   * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
   */

  // SHA-256 of the complete upstream JSON: 5093af89bdf85b41695836abbab8f94ec899598cd9d5d8f0ebf34a7c17a1924e
  private val nghttp2Story02: Vector[(String, Vector[(String, String)])] = Vector(
    (
      "828641871d23f67a9721e9847abcd07f66a281b0dae053fad0321aa49d13fda992a49685340c8a6a" +
        "dca7e28102ef7da9677b8171707f6a62293a9d810020004015309ac2ca7f2c05c5c153b0497ca589" +
        "d34d1f43aeba0c41a4c7a98f33a69a3fdf9a68fa1d75d0620d263d4c79a68fbed00177febe58f9fb" +
        "ed00177b518b2d4b70ddf45abefb4005db90408721eaa8a4498f5788ea52d6b0e83772ff",
      Vector(
        ":method" -> "GET",
        ":scheme" -> "http",
        ":authority" -> "amazon.com",
        ":path" -> "/",
        "user-agent" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0",
        "accept" -> "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "accept-language" -> "en-US,en;q=0.5",
        "accept-encoding" -> "gzip, deflate",
        "connection" -> "keep-alive",
      ),
    ),
    (
      "82864191996293cae6a473150b0e91fb3d4b90f4ff04ab60d48e62a18c4c002c4d51d88ca321ea62" +
        "e94643d5babb0c92adc372c00af17168017c0cb6cb712f5d537fc2539a352398ac5754df46a47315" +
        "8f9fbed00177bebe58f9fbed00176fc190c073919d29aee30c78f1e171d23f67a9721e963f",
      Vector(
        ":method" -> "GET",
        ":scheme" -> "http",
        ":authority" -> "g-ecx.images-amazon.com",
        ":path" -> "/images/G/01/gno/beacon/BeaconSprite-US-01._V401903535_.png",
        "user-agent" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0",
        "accept" -> "image/png,image/*;q=0.8,*/*;q=0.5",
        "accept-language" -> "en-US,en;q=0.5",
        "accept-encoding" -> "gzip, deflate",
        "connection" -> "keep-alive",
        "referer" -> "http://www.amazon.com/",
      ),
    ),
    (
      "8286c004ad60d48e62a18c4c002c795a83907415821e9a4f5309b07522b1d85a92b566f25a178b8b" +
        "2f38fb4269c6a25e634bc4bfc290c1be",
      Vector(
        ":method" -> "GET",
        ":scheme" -> "http",
        ":authority" -> "g-ecx.images-amazon.com",
        ":path" -> "/images/G/01/x-locale/common/transparent-pixel._V386942464_.gif",
        "user-agent" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0",
        "accept" -> "image/png,image/*;q=0.8,*/*;q=0.5",
        "accept-language" -> "en-US,en;q=0.5",
        "accept-encoding" -> "gzip, deflate",
        "connection" -> "keep-alive",
        "referer" -> "http://www.amazon.com/",
      ),
    ),
    (
      "3fb60a8286c004bf60d48e62a18c4c002c1a9982260e99cb63121903424b62d61683165619001621" +
        "e8b69a9840ea93d2d61683165899003cbadaf171680071e7da7c312f5d537fc4bfc290c1be",
      Vector(
        ":method" -> "GET",
        ":scheme" -> "http",
        ":authority" -> "g-ecx.images-amazon.com",
        ":path" -> "/images/G/01/img12/other/disaster-relief/300-column/sandy-relief_300x75._V400689491_.png",
        "user-agent" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0",
        "accept" -> "image/png,image/*;q=0.8,*/*;q=0.5",
        "accept-language" -> "en-US,en;q=0.5",
        "accept-encoding" -> "gzip, deflate",
        "connection" -> "keep-alive",
        "referer" -> "http://www.amazon.com/",
      ),
    ),
    (
      "8286418bf1e3c2e3a47ecf52e43d3f84c5c4c390c2",
      Vector(
        ":method" -> "GET",
        ":scheme" -> "http",
        ":authority" -> "www.amazon.com",
        ":path" -> "/",
        "user-agent" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0",
        "accept" -> "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "accept-language" -> "en-US,en;q=0.5",
        "accept-encoding" -> "gzip, deflate",
        "connection" -> "keep-alive",
      ),
    ),
    (
      "8286c104ad60d48e62a18c4c002c795a83907415821e9a4f5309b07522b1d85a92b566f25a178b88" +
        "5f109969c75b89798d2fc5c0c390c2bf",
      Vector(
        ":method" -> "GET",
        ":scheme" -> "http",
        ":authority" -> "g-ecx.images-amazon.com",
        ":path" -> "/images/G/01/x-locale/common/transparent-pixel._V192234675_.gif",
        "user-agent" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0",
        "accept" -> "image/png,image/*;q=0.8,*/*;q=0.5",
        "accept-language" -> "en-US,en;q=0.5",
        "accept-encoding" -> "gzip, deflate",
        "connection" -> "keep-alive",
        "referer" -> "http://www.amazon.com/",
      ),
    ),
    (
      "3f8b158286c104c160d48e62a18c4c002c1a9982261139ca86103a0a888bdcb5250c0431547eec04" +
        "0c82284842a107b0c546bdbab46a8b172b0d34e95e2e2d000e09c7db044bcc697fc5c0c390c2bf",
      Vector(
        ":method" -> "GET",
        ":scheme" -> "http",
        ":authority" -> "g-ecx.images-amazon.com",
        ":path" -> "/images/G/01/img12/shoes/sales_events/11_nov/1030_AccessoriesPROMO_GWright._V400626950_.gif",
        "user-agent" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0",
        "accept" -> "image/png,image/*;q=0.8,*/*;q=0.5",
        "accept-language" -> "en-US,en;q=0.5",
        "accept-encoding" -> "gzip, deflate",
        "connection" -> "keep-alive",
        "referer" -> "http://www.amazon.com/",
      ),
    ),
    (
      "8286c104ac60d48e62a18c4c002c436a4f49d26ee562c3a4e862fdb60c85a287000882202f1710be" +
        "2101a75c6a25fa5737c5c0c390c2bf",
      Vector(
        ":method" -> "GET",
        ":scheme" -> "http",
        ":authority" -> "g-ecx.images-amazon.com",
        ":path" -> "/images/G/01/Automotive/rotos/Duracell600_120._V192204764_.jpg",
        "user-agent" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0",
        "accept" -> "image/png,image/*;q=0.8,*/*;q=0.5",
        "accept-language" -> "en-US,en;q=0.5",
        "accept-encoding" -> "gzip, deflate",
        "connection" -> "keep-alive",
        "referer" -> "http://www.amazon.com/",
      ),
    ),
    (
      "8286c104b060d48e62a18c4c002c5a662838e4c9548620d27b10c5071c992a90c41a4f62d40ec98a" +
        "bc5c42f882fb6d3c089798d2ffc5c0c390c2bf",
      Vector(
        ":method" -> "GET",
        ":scheme" -> "http",
        ":authority" -> "g-ecx.images-amazon.com",
        ":path" -> "/images/G/01/ui/loadIndicators/loadIndicator-large._V192195480_.gif",
        "user-agent" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0",
        "accept" -> "image/png,image/*;q=0.8,*/*;q=0.5",
        "accept-language" -> "en-US,en;q=0.5",
        "accept-encoding" -> "gzip, deflate",
        "connection" -> "keep-alive",
        "referer" -> "http://www.amazon.com/",
      ),
    ),
    (
      "8286418f293cae6a473150b0e91fb3d4b90f4f049a60d48e62a18c8c341c7fab69beb6ee19d78b76" +
        "70b2dc4bf4ae6fc6c1c490c3c0",
      Vector(
        ":method" -> "GET",
        ":scheme" -> "http",
        ":authority" -> "ecx.images-amazon.com",
        ":path" -> "/images/I/41HZ-ND-SUL._SL135_.jpg",
        "user-agent" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0",
        "accept" -> "image/png,image/*;q=0.8,*/*;q=0.5",
        "accept-language" -> "en-US,en;q=0.5",
        "accept-encoding" -> "gzip, deflate",
        "connection" -> "keep-alive",
        "referer" -> "http://www.amazon.com/",
      ),
    ),
  )

  // SHA-256 of the complete upstream JSON: 5649a28baa8569d927db505b754179ced762a0c80a1e002eea9e8777fd9f68bd
  private val goStory02: Vector[(String, Vector[(String, String)])] = Vector(
    (
      "0085b9495339e483c5837f0085b8824e5a4b839d29af0088b83b5339ec327d7f871d23f67a9721e9" +
        "0084b958d33f81630087b505b161cc5a93bcd07f66a281b0dae053fad0321aa49d13fda992a49685" +
        "340c8a6adca7e28102ef7da9677b8171707f6a62293a9d810020004015309ac2ca7f2c05c5c10084" +
        "19085ad3b0497ca589d34d1f43aeba0c41a4c7a98f33a69a3fdf9a68fa1d75d0620d263d4c79a68f" +
        "bed00177febe58f9fbed00177b008b19085ad2b503aa6b47317f8b2d4b70ddf45abefb4005db008b" +
        "19085ad2b16a21e435537f8a9bd9abfa5242cb40d25f008721eaa8a4498f5788ea52d6b0e83772ff",
      Vector(
        ":method" -> "GET",
        ":scheme" -> "http",
        ":authority" -> "amazon.com",
        ":path" -> "/",
        "user-agent" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0",
        "accept" -> "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "accept-language" -> "en-US,en;q=0.5",
        "accept-encoding" -> "gzip, deflate",
        "connection" -> "keep-alive",
      ),
    ),
    (
      "0085b9495339e483c5837f0085b8824e5a4b839d29af0088b83b5339ec327d7f91996293cae6a473" +
        "150b0e91fb3d4b90f4ff0084b958d33fab60d48e62a18c4c002c4d51d88ca321ea62e94643d5babb" +
        "0c92adc372c00af17168017c0cb6cb712f5d537f0087b505b161cc5a93bcd07f66a281b0dae053fa" +
        "d0321aa49d13fda992a49685340c8a6adca7e28102ef7da9677b8171707f6a62293a9d8100200040" +
        "15309ac2ca7f2c05c5c1008419085ad39a352398ac5754df46a473158f9fbed00177bebe58f9fbed" +
        "00176f008b19085ad2b503aa6b47317f8b2d4b70ddf45abefb4005db008b19085ad2b16a21e43553" +
        "7f8a9bd9abfa5242cb40d25f008721eaa8a4498f5788ea52d6b0e83772ff0085b0b296c2d9919d29" +
        "aee30c78f1e171d23f67a9721e963f",
      Vector(
        ":method" -> "GET",
        ":scheme" -> "http",
        ":authority" -> "g-ecx.images-amazon.com",
        ":path" -> "/images/G/01/gno/beacon/BeaconSprite-US-01._V401903535_.png",
        "user-agent" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0",
        "accept" -> "image/png,image/*;q=0.8,*/*;q=0.5",
        "accept-language" -> "en-US,en;q=0.5",
        "accept-encoding" -> "gzip, deflate",
        "connection" -> "keep-alive",
        "referer" -> "http://www.amazon.com/",
      ),
    ),
  )

}
