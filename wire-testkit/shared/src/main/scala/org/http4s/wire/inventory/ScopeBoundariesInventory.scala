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

import SpecInventory.Entry

private[wire] object ScopeBoundariesInventory {
  val all: Vector[Entry] = part0

  private def part0: Vector[Entry] = Vector(
    Entry(
      "scope-h2-alpn-identifier",
      "9113",
      "3.1",
      "both",
      "syntax",
      "The h2 identifier names HTTP/2 over TLS and is serialized as 0x68 0x32 in ALPN.",
      "excluded",
      Vector(),
      "TLS/ALPN identification is outside the plaintext prior-knowledge baseline.",
    ),
    Entry(
      "scope-h2c-upgrade-deprecated",
      "9113",
      "3.1",
      "both",
      "informative",
      "The historical h2c Upgrade token and HTTP2-Settings header usage are deprecated.",
      "excluded",
      Vector(),
      "HTTP/1-to-HTTP/2 upgrade negotiation is outside the explicitly selected prior-knowledge mode; deprecation alone is not interpreted as a mandatory endpoint rejection rule.",
    ),
  )

}
