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
package servlet

class ServletContainerSuite extends Http4sSuite {
  import ServletContainer.prefixMapping

  test("prefixMapping should append /* when prefix does not have trailing slash") {
    assert(prefixMapping("/foo") == "/foo/*")
  }

  test("prefixMapping should append * when prefix has trailing slash") {
    assert(prefixMapping("/") == "/*")
  }
}
