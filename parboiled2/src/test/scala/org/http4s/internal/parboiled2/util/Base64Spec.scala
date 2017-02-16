/*
 * Copyright (C) 2009-2013 Mathias Doenitz, Alexander Myltsev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.internal.parboiled2.util

import java.nio.charset.StandardCharsets

import org.specs2.mutable.Specification
import org.specs2.specification._
import scala.util._

class Base64Spec extends Specification {
  private val testVectors = Map(
    "" -> "",
    "f" -> "Zg==",
    "fo" -> "Zm8=",
    "foo" -> "Zm9v",
    "foob" -> "Zm9vYg==",
    "fooba" -> "Zm9vYmE=",
    "foobar" -> "Zm9vYmFy",
    "@@ Hello @@ world @@!" -> "QEAgSGVsbG8gQEAgd29ybGQgQEAh"
  )

  "Base64 should" >> {
    "work as expected" in {
      testVectors.map { case (expectedDecoded, expectedEncoded) =>
        val expectedDecodedBytes = expectedDecoded.getBytes(StandardCharsets.UTF_8)

        val encoded = Base64.rfc2045().encodeToString(expectedDecodedBytes, false)

        expectedEncoded === encoded and
        expectedDecodedBytes === Base64.rfc2045().decode(encoded.toCharArray) and
        expectedDecodedBytes === Base64.rfc2045().decodeFast(encoded.toCharArray)
      }.reduceLeft(_ and _)
    }
  }
}
