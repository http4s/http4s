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

import cats.effect.IO

import java.nio.charset.StandardCharsets
import java.util.Base64

class BasicCredentialsSuite extends Http4sSuite {
  test("fromString should decode tokens that are not UTF-8 as ISO-8859-1") {
    val token = Base64.getEncoder.encodeToString("Åsa:lösen".getBytes(StandardCharsets.ISO_8859_1))
    assertEquals(
      BasicCredentials.fromString(token),
      Some(BasicCredentials("Åsa", "lösen", StandardCharsets.ISO_8859_1)),
    )
  }

  test("fromString should decode tokens correctly when decoding concurrently") {
    val credentials = BasicCredentials("Åsa", "€uro")
    val decode = IO(BasicCredentials.fromString(credentials.token))
    decode.replicateA(1000).parReplicateA(16).map { decoded =>
      assertEquals(decoded.flatten.distinct, List(Some(credentials)))
    }
  }
}
