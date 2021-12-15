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

import org.http4s.syntax.all._

class UriSuite extends Http4sSuite {
  test("toOriginForm strips scheme and authority") {
    uri"http://example.com/foo?q".toOriginForm == uri"/foo?q"
  }

  test("toOriginForm strips fragment") {
    uri"/foo?q#top".toOriginForm == uri"/foo?q"
  }

  test("toOriginForm infers an empty path") {
    uri"http://example.com".toOriginForm == uri"/"
  }

  test("toOriginForm infers paths relative to root") {
    uri"dubious".toOriginForm == uri"/dubious"
  }

  test("Use lazy query model parsing in uri parsing") {
    val ori = "http://domain.com/path?param1=asd;fgh"
    val res = org.http4s.Uri.unsafeFromString(ori).renderString
    assertEquals(ori, res)
  }
}
