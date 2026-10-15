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

import cats.effect.testkit.TestControl

class Http2CleanupLifetimeSuite extends RuntimeDiagnostics.Suite {
  import Http2CleanupFixture._

  // All 109 acquired-response callers: cleanup EOF is not a protocol rejection.
  for {
    id <- Vector(
      "h2.client.reject-preface-first-ping",
      "h2.client.reject-priority-length-4",
      "h2.client.reject-priority-length-6",
      "h2.client.idle-data",
      "h2.client.idle-window-update",
      "h2.client.unpromised-server-headers",
      "h2.client.zero-connection-window-update",
      "h2.client.zero-stream-window-update",
      "h2.client.connection-window-overflow",
      "h2.client.stream-window-overflow",
      "h2.client.settings-stream-window-overflow",
      "h2.client.hpack-reject-index-zero",
      "h2.client.hpack-reject-index-empty-dynamic",
      "h2.client.hpack-reject-index-integer-truncated",
      "h2.client.hpack-reject-incremental-name-integer-truncated",
      "h2.client.hpack-reject-without-name-integer-truncated",
      "h2.client.hpack-reject-never-name-integer-truncated",
      "h2.client.hpack-reject-index-integer-huge",
      "h2.client.hpack-reject-name-truncated",
      "h2.client.hpack-reject-name-length-truncated",
      "h2.client.hpack-reject-value-truncated",
      "h2.client.hpack-reject-value-missing",
      "h2.client.hpack-reject-string-length-truncated",
      "h2.client.hpack-reject-huffman-string-truncated",
      "h2.client.hpack-reject-incremental-name-index",
      "h2.client.hpack-reject-without-name-index",
      "h2.client.hpack-reject-never-name-index",
      "h2.client.hpack-reject-huffman-value-eos",
      "h2.client.hpack-reject-huffman-name-eos",
      "h2.client.hpack-reject-huffman-padding-eight",
      "h2.client.hpack-reject-huffman-name-padding-eight",
      "h2.client.hpack-reject-huffman-value-padding",
      "h2.client.hpack-reject-huffman-name-padding",
      "h2.client.hpack-reject-update-after-index",
      "h2.client.hpack-reject-update-after-literal",
      "h2.client.hpack-reject-update-integer-truncated",
      "h2.client.hpack-reject-update-above-advertised",
      "h2.client.hpack-reject-index-beyond-populated",
      "h2.client.hpack-reject-without-not-stored",
      "h2.client.hpack-reject-never-not-stored",
      "h2.client.hpack-reject-evicted-oldest-index",
      "h2.client.hpack-reject-shrink-evicted-index",
      "h2.client.hpack-reject-zero-restored-index",
      "h2.client.hpack-reject-oversize-clears-table",
      "h2.client.hpack-reject-huffman-compressed-size",
      "h2.client.hpack-reject-initial-dynamic-index",
    )
    close <- Vector(false, true)
  } test(s"client response cleanup cannot supply wire rejection: $id/close=$close") {
    TestControl.executeEmbed(cleanupLifetimeProbe(id, close))
  }

  private val messageCleanupCases = Vector(
    ("h2.client.reject-uppercase-field-name", 1, "negative"),
    ("h2.client.reject-field-value-nul", 1, "negative"),
    ("h2.client.reject-field-value-cr", 1, "negative"),
    ("h2.client.reject-field-value-lf", 1, "negative"),
    ("h2.client.reject-connection-field", 1, "negative"),
    ("h2.client.reject-proxy-connection-field", 1, "negative"),
    ("h2.client.reject-keep-alive-field", 1, "negative"),
    ("h2.client.reject-transfer-encoding-field", 1, "negative"),
    ("h2.client.reject-upgrade-field", 1, "negative"),
    ("h2.client.reject-field-name-nul", 1, "negative"),
    ("h2.client.reject-field-name-tab", 1, "negative"),
    ("h2.client.reject-field-name-space", 1, "negative"),
    ("h2.client.reject-field-name-control-boundary", 1, "negative"),
    ("h2.client.reject-field-name-uppercase-boundary", 1, "negative"),
    ("h2.client.reject-field-name-del", 1, "negative"),
    ("h2.client.reject-field-name-obs-text-low", 1, "negative"),
    ("h2.client.reject-field-name-obs-text-high", 1, "negative"),
    ("h2.client.reject-field-name-colon", 1, "negative"),
    ("h2.client.reject-field-value-leading-space", 1, "negative"),
    ("h2.client.reject-field-value-trailing-space", 1, "negative"),
    ("h2.client.reject-field-value-leading-tab", 1, "negative"),
    ("h2.client.reject-field-value-trailing-tab", 1, "negative"),
    ("h2.client.validate-field-empty-name", 1, "negative"),
    ("h2.client.validate-field-separator-name", 1, "negative"),
    ("h2.client.reject-content-length-underflow", 2, "negative"),
    ("h2.client.reject-content-length-overflow", 2, "negative"),
    ("h2.client.reject-trailers-without-end-stream", 4, "negative"),
    ("h2.client.reject-trailer-pseudo-header", 3, "negative"),
    ("h2.client.reject-trailer-uppercase-name", 3, "negative"),
    ("h2.client.reject-trailer-prohibited-field", 3, "negative"),
    ("h2.client.reject-undefined-pseudo-header", 1, "negative"),
    ("h2.client.reject-pseudo-header-after-regular-field", 1, "negative"),
    ("h2.client.reject-headers-stream-zero", 1, "negative"),
    ("h2.client.reject-headers-padding-equals-payload", 2, "negative"),
    ("h2.client.reject-headers-padding-exceeds-payload", 2, "negative"),
    ("h2.client.reject-headers-missing-pad-length", 2, "negative"),
    ("h2.client.reject-headers-short-priority", 2, "negative"),
    ("h2.client.reject-orphan-continuation", 1, "negative"),
    ("h2.client.reject-continuation-stream-zero", 2, "negative"),
    ("h2.client.reject-continuation-different-stream", 3, "negative"),
    ("h2.client.reject-headers-interrupted-by-ping", 3, "negative"),
    ("h2.client.reject-continuation-interrupted-by-settings", 4, "negative"),
    ("h2.client.reject-response-te", 1, "negative"),
    ("h2.client.reject-missing-status", 1, "negative"),
    ("h2.client.reject-request-method-pseudo-header", 1, "negative"),
    ("h2.client.reject-request-scheme-pseudo-header", 1, "negative"),
    ("h2.client.reject-request-authority-pseudo-header", 1, "negative"),
    ("h2.client.reject-request-path-pseudo-header", 1, "negative"),
    ("h2.client.reject-repeated-status-identical", 1, "negative"),
    ("h2.client.reject-repeated-status-different", 1, "negative"),
    ("h2.client.reject-status-short", 1, "negative"),
    ("h2.client.reject-status-long", 1, "negative"),
    ("h2.client.reject-status-non-digit", 1, "negative"),
    ("h2.client.reject-informational-end-stream", 1, "negative"),
    ("h2.client.reject-switching-protocols", 2, "negative"),
    ("h2.client.reject-push-stream-zero", 2, "push"),
    ("h2.client.reject-push-odd-promised-stream", 2, "push"),
    ("h2.client.reject-push-unsafe-method", 2, "push"),
    ("h2.client.reject-push-request-content", 2, "push"),
    ("h2.client.reject-push-missing-method", 2, "push"),
    ("h2.client.reject-push-invalid-field", 2, "push"),
    ("h2.client.reject-push-unauthoritative-origin", 2, "push"),
    ("h2.client.headers-nonzero-padding-choice", 1, "padding-choice"),
  )

  for {
    entry <- messageCleanupCases
    (id, frames, kind) = entry
    enabled <- if (kind == "push") Vector(false, true) else Vector(true)
    close <- Vector(false, true)
  } test(s"message response cleanup cannot supply wire rejection: $id/push=$enabled/close=$close") {
    TestControl.executeEmbed(
      messageCleanupLifetimeProbe(id, frames, close, enabled, kind == "push")
    )
  }

  for (close <- Vector(false, true)) {
    test(s"socket response lifetime: HPACK/close=$close") {
      cleanupLifetimeProbe("h2.client.hpack-reject-index-zero", close, inMemory = false)
    }
    test(s"socket response lifetime: Message/close=$close") {
      messageCleanupLifetimeProbe(
        "h2.client.reject-missing-status",
        1,
        close,
        true,
        false,
        inMemory = false,
      )
    }
  }

}
