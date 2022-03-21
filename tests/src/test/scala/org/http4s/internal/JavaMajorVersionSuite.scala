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
