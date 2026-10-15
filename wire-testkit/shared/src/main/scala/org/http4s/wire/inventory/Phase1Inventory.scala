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

private[wire] object Phase1Inventory {
  val all: Vector[Entry] = part0

  private def part0: Vector[Entry] = Vector(
    Entry(
      "9112-origin-smoke",
      "9112",
      "2.1",
      "server",
      "syntax",
      "Accept a complete origin-form request with Host and an extension field",
      "covered",
      Vector(SmokeCases.h1ServerGet),
      "Initial representative boundary; broader variants require separate rows.",
    ),
    Entry(
      "9112-field-colon-space",
      "9112",
      "5.1",
      "server",
      "MUST",
      "Respond 400 to whitespace between request field name and colon",
      "covered",
      Vector(SmokeCases.h1ServerRejectFieldNameWhitespace),
      "Initial representative boundary; broader variants require separate rows.",
    ),
    Entry(
      "9112-response-content-length",
      "9112",
      "6.3",
      "client",
      "MUST",
      "Read exactly the declared response octets when Content-Length controls framing",
      "covered",
      Vector(SmokeCases.h1ClientFixedLength),
      "Initial representative boundary; broader variants require separate rows.",
    ),
    Entry(
      "9112-conflicting-length",
      "9112",
      "6.3",
      "client",
      "MUST",
      "Discard a response containing conflicting Content-Length values",
      "covered",
      Vector(SmokeCases.h1ClientRejectConflictingContentLength),
      "Native acquisition/body rejection is classified before response release; cleanup failure cannot replace successful consumption. Initial representative boundary; broader variants require separate rows.",
    ),
    Entry(
      "9112-missing-host",
      "9112",
      "3.2",
      "server",
      "MUST",
      "Respond 400 to an HTTP/1.1 request with no Host field",
      "covered",
      Vector(BaselineCases.h1ServerMissingHost),
      "Initial representative boundary; broader variants require separate rows.",
    ),
    Entry(
      "9113-ping-echo",
      "9113",
      "6.7",
      "server",
      "MUST",
      "Acknowledge a non-ACK PING with the same eight octets",
      "covered",
      Vector(SmokeCases.h2ServerPing),
      "Initial representative boundary; broader variants require separate rows.",
    ),
    Entry(
      "9113-ping-size",
      "9113",
      "6.7",
      "server",
      "MUST",
      "Treat a non-eight-byte PING as a connection error",
      "covered",
      Vector(SmokeCases.h2ServerRejectPingLength),
      "Initial representative boundary; broader variants require separate rows. Rejection observations validate role-specific control frames and all captured tails, including nonincreasing GOAWAY last-stream identifiers. A well-formed graceful GOAWAY followed by genuine EOF is permitted; a GOAWAY on an open transport or cleanup-created EOF is insufficient. Explicit nonzero error codes retain the case-specific and generic alternatives.",
    ),
    Entry(
      "9113-response-sequence",
      "9113",
      "8.1",
      "client",
      "syntax",
      "Consume response HEADERS followed by DATA terminated by END_STREAM",
      "covered",
      Vector(SmokeCases.h2ClientHeadersAndData),
      "Initial representative boundary; broader variants require separate rows.",
    ),
    Entry(
      "9113-response-length-mismatch",
      "9113",
      "8.1.1",
      "client",
      "MUST",
      "Reject a completed response whose declared content-length disagrees with DATA length",
      "covered",
      Vector(SmokeCases.h2ClientRejectContentLengthMismatch),
      "Native acquisition/body rejection and independent RST_STREAM, GOAWAY plus closure, or transport closure are observed before response release; cleanup cannot supply rejection evidence. Initial representative boundary; broader variants require separate rows. Rejection observations validate role-specific control frames and all captured tails, including nonincreasing GOAWAY last-stream identifiers. A well-formed graceful GOAWAY followed by genuine EOF is permitted; a GOAWAY on an open transport or cleanup-created EOF is insufficient. Explicit nonzero error codes retain the case-specific and generic alternatives.",
    ),
  )

}
