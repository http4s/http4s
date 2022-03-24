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

package org.http4s.internal

import org.http4s.Http4sSuite

class JavaMajorVersionSuite extends Http4sSuite {
  test("parseJavaMajorVersion") {
    // Through Java 8, it was "1."
    assertEquals(parseJavaMajorVersion("1.8.0_292"), Some(8))
    // Afterward, it's as expected
    assertEquals(parseJavaMajorVersion("17.0.1"), Some(17))
    // Gracefully handle trash
    assertEquals(parseJavaMajorVersion("trash"), None)
  }
}
