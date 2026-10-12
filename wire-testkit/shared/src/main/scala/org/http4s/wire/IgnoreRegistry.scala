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

final case class IgnoredCase(
    id: String,
    platforms: Set[String],
    observed: String,
    revision: String,
    status: String,
    tracking: String,
)

/** The only source of ignores. Full comparison suites never consult this registry. */
object IgnoreRegistry {
  private val phase3A4Evidence =
    "54d96bc0687624a656f2d29e72fb7408ab33815f:c53ffae541df499791de7494e94a51b53cfcddf07353079964009d7f84d4060f"

  private val completionPaddingEvidence =
    "54d96bc0687624a656f2d29e72fb7408ab33815f:66b8934ace465bc7ed58d12391e20428ac1086e1be387baefb4b26c6da7da35e"

  private val finalComparisonEvidence =
    "54d96bc0687624a656f2d29e72fb7408ab33815f:2375bd13ff35b3ce3e5e174601ca1ad1f554a4fdbbb25f5e3b2c6b25a506df6f"

  private val nativeScala212Evidence =
    "54d96bc0687624a656f2d29e72fb7408ab33815f:ee7c8ec851956e02a2bfc99c374b5accefc1594afe9c5a8c7d68d4937890797b"

  val http1: Vector[IgnoredCase] = Vector(
    IgnoredCase(
      id = "h1.client.chunk-extension-bad-whitespace",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); ChunkedEncodingError rejects valid chunk-extension BWS",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h1-client-chunk-extension-bad-whitespace",
    ),
    IgnoredCase(
      id = "h1.client.error-response-during-upload",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (SHOULD; no deviation established); 8-second timeout after observed 32768-byte prefix and complete 413/Connection: close; stopping/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h1-client-error-response-during-upload",
    ),
    IgnoredCase(
      id = "h1.client.generated-asterisk-form",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); public Ember URI/encoder binding sends OPTIONS /* instead of OPTIONS *",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h1-client-generated-asterisk-form",
    ),
    IgnoredCase(
      id = "h1.client.interaction-interim-next-request-association",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); expected: 201; observed: 103",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking =
        "docs/wire-tck/known-failures.md#h1-client-interaction-interim-next-request-association",
    ),
    IgnoredCase(
      id = "h1.client.obsolete-fold-multiple",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); InvalidHeaderWhitespace rejects response requiring obs-fold acceptance",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h1-client-obsolete-fold-multiple",
    ),
    IgnoredCase(
      id = "h1.client.obsolete-fold-space",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); InvalidHeaderWhitespace rejects response requiring obs-fold acceptance",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h1-client-obsolete-fold-space",
    ),
    IgnoredCase(
      id = "h1.client.obsolete-fold-tab",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); InvalidHeaderWhitespace rejects response requiring obs-fold acceptance",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h1-client-obsolete-fold-tab",
    ),
    IgnoredCase(
      id = "h1.server.absolute-form-empty-host",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); an empty http URI host was accepted: Some(200)",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h1-server-absolute-form-empty-host",
    ),
    IgnoredCase(
      id = "h1.server.chunk-extension-bad-whitespace",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); expected: 200; observed: 500",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h1-server-chunk-extension-bad-whitespace",
    ),
    IgnoredCase(
      id = "h1.server.duplicate-host-case-insensitive",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); expected: 400; observed: 200",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h1-server-duplicate-host-case-insensitive",
    ),
    IgnoredCase(
      id = "h1.server.duplicate-host-different",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); expected: 400; observed: 200",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h1-server-duplicate-host-different",
    ),
    IgnoredCase(
      id = "h1.server.duplicate-host-identical",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); expected: 400; observed: 200",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h1-server-duplicate-host-identical",
    ),
    IgnoredCase(
      id = "h1.server.field-name-delimiter",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed recommendation deviation (SHOULD; not MUST); expected: 400; observed: 200",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h1-server-field-name-delimiter",
    ),
    IgnoredCase(
      id = "h1.server.head-response-has-no-content",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); additional bytes followed a response requiring connection closure",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h1-server-head-response-has-no-content",
    ),
    IgnoredCase(
      id = "h1.server.host-comma",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); expected: 400; observed: 200",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h1-server-host-comma",
    ),
    IgnoredCase(
      id = "h1.server.host-nondigit-port",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); expected: 400; observed: 200",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h1-server-host-nondigit-port",
    ),
    IgnoredCase(
      id = "h1.server.host-path",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); expected: 400; observed: 200",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h1-server-host-path",
    ),
    IgnoredCase(
      id = "h1.server.host-space",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); expected: 400; observed: 200",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h1-server-host-space",
    ),
    IgnoredCase(
      id = "h1.server.host-unclosed-ipv6-literal",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); expected: 400; observed: 200",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h1-server-host-unclosed-ipv6-literal",
    ),
    IgnoredCase(
      id = "h1.server.host-userinfo",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); expected: 400; observed: 200",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h1-server-host-userinfo",
    ),
    IgnoredCase(
      id = "h1.server.http10-close-overrides-keep-alive",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h1-server-http10-close-overrides-keep-alive",
    ),
    IgnoredCase(
      id = "h1.server.http10-response-has-no-transfer-encoding",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); expected: Vector(); observed: Vector(chunked)",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking =
        "docs/wire-tck/known-failures.md#h1-server-http10-response-has-no-transfer-encoding",
    ),
    IgnoredCase(
      id = "h1.server.leading-empty-line",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed recommendation deviation (SHOULD; not MUST); expected: 200; observed: 400",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h1-server-leading-empty-line",
    ),
    IgnoredCase(
      id = "h1.server.missing-host",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); expected: 400; observed: 200",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h1-server-missing-host",
    ),
    IgnoredCase(
      id = "h1.server.no-content-response-has-no-content",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); additional bytes followed a response requiring connection closure",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h1-server-no-content-response-has-no-content",
    ),
    IgnoredCase(
      id = "h1.server.not-modified-response-has-no-content",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); additional bytes followed a response requiring connection closure",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h1-server-not-modified-response-has-no-content",
    ),
    IgnoredCase(
      id = "h1.server.unsupported-transfer-coding",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed recommendation deviation (SHOULD; not MUST); expected unsupported coding or length-required policy, observed 400",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h1-server-unsupported-transfer-coding",
    ),
  )

  val http2: Vector[IgnoredCase] = Vector(
    IgnoredCase(
      id = "h2.client.connection-credit-independent",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); selected run after completion-credit correction times out at 8 seconds; required control/progress witness remains incomplete",
      revision = completionPaddingEvidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-client-connection-credit-independent",
    ),
    IgnoredCase(
      id = "h2.client.connection-window-overflow",
      platforms = Set("native"),
      observed =
        "native Scala 2.12.21: provisional observation limit (MUST; no deviation established); 8-second timeout after connection WINDOW_UPDATE overflow stimulus; only SETTINGS ACK observed, rejection/closure unestablished",
      revision = nativeScala212Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-client-connection-window-overflow",
    ),
    IgnoredCase(
      id = "h2.client.hpack-encoder-minimum-before-restore",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); invalid HPACK index after acknowledged encoder table reduction",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-hpack-encoder-minimum-before-restore",
    ),
    IgnoredCase(
      id = "h2.client.hpack-encoder-reduction-zero",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); invalid HPACK index after acknowledged encoder table reduction",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-hpack-encoder-reduction-zero",
    ),
    IgnoredCase(
      id = "h2.client.hpack-reject-huffman-name-padding-eight",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); target client consumed malformed HPACK",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-hpack-reject-huffman-name-padding-eight",
    ),
    IgnoredCase(
      id = "h2.client.hpack-reject-huffman-padding-eight",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); target client consumed malformed HPACK",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-hpack-reject-huffman-padding-eight",
    ),
    IgnoredCase(
      id = "h2.client.hpack-reject-huffman-string-truncated",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); target client consumed malformed HPACK",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-hpack-reject-huffman-string-truncated",
    ),
    IgnoredCase(
      id = "h2.client.hpack-reject-incremental-name-integer-truncated",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); target client consumed malformed HPACK",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking =
        "docs/wire-tck/known-failures.md#h2-client-hpack-reject-incremental-name-integer-truncated",
    ),
    IgnoredCase(
      id = "h2.client.hpack-reject-index-integer-truncated",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); target client consumed malformed HPACK",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-hpack-reject-index-integer-truncated",
    ),
    IgnoredCase(
      id = "h2.client.hpack-reject-name-length-truncated",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); target client consumed malformed HPACK",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-hpack-reject-name-length-truncated",
    ),
    IgnoredCase(
      id = "h2.client.hpack-reject-name-truncated",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); target client consumed malformed HPACK",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-hpack-reject-name-truncated",
    ),
    IgnoredCase(
      id = "h2.client.hpack-reject-never-name-integer-truncated",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); target client consumed malformed HPACK",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking =
        "docs/wire-tck/known-failures.md#h2-client-hpack-reject-never-name-integer-truncated",
    ),
    IgnoredCase(
      id = "h2.client.hpack-reject-string-length-truncated",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); target client consumed malformed HPACK",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-hpack-reject-string-length-truncated",
    ),
    IgnoredCase(
      id = "h2.client.hpack-reject-update-after-index",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); target client consumed malformed HPACK",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-hpack-reject-update-after-index",
    ),
    IgnoredCase(
      id = "h2.client.hpack-reject-update-after-literal",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); target client consumed malformed HPACK",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-hpack-reject-update-after-literal",
    ),
    IgnoredCase(
      id = "h2.client.hpack-reject-value-missing",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); target client consumed malformed HPACK",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-hpack-reject-value-missing",
    ),
    IgnoredCase(
      id = "h2.client.hpack-reject-value-truncated",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); target client consumed malformed HPACK",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-hpack-reject-value-truncated",
    ),
    IgnoredCase(
      id = "h2.client.hpack-reject-without-name-integer-truncated",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); target client consumed malformed HPACK",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking =
        "docs/wire-tck/known-failures.md#h2-client-hpack-reject-without-name-integer-truncated",
    ),
    IgnoredCase(
      id = "h2.client.interaction-eager-server-settings",
      platforms = Set("native"),
      observed =
        "native Scala 2.12.21: confirmed mandatory deviation (MUST); client preface followed by SETTINGS ACK before initial non-ACK SETTINGS",
      revision = nativeScala212Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-interaction-eager-server-settings",
    ),
    IgnoredCase(
      id = "h2.client.interaction-eager-zero-window-upload",
      platforms = Set("native"),
      observed =
        "native: confirmed mandatory deviation (MUST/MUST NOT); DATA consumed credit while the stream window was zero",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-interaction-eager-zero-window-upload",
    ),
    IgnoredCase(
      id = "h2.client.message-empty-trailers",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (syntax/MUST); COMPRESSION_ERROR rejects valid empty trailing HEADERS",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-message-empty-trailers",
    ),
    IgnoredCase(
      id = "h2.client.reject-connection-field",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-connection-field",
    ),
    IgnoredCase(
      id = "h2.client.reject-field-name-colon",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-field-name-colon",
    ),
    IgnoredCase(
      id = "h2.client.reject-field-name-control-boundary",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-field-name-control-boundary",
    ),
    IgnoredCase(
      id = "h2.client.reject-field-name-del",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-field-name-del",
    ),
    IgnoredCase(
      id = "h2.client.reject-field-name-nul",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-field-name-nul",
    ),
    IgnoredCase(
      id = "h2.client.reject-field-name-obs-text-high",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-field-name-obs-text-high",
    ),
    IgnoredCase(
      id = "h2.client.reject-field-name-obs-text-low",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-field-name-obs-text-low",
    ),
    IgnoredCase(
      id = "h2.client.reject-field-name-space",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-field-name-space",
    ),
    IgnoredCase(
      id = "h2.client.reject-field-name-tab",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-field-name-tab",
    ),
    IgnoredCase(
      id = "h2.client.reject-field-name-uppercase-boundary",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-field-name-uppercase-boundary",
    ),
    IgnoredCase(
      id = "h2.client.reject-field-value-cr",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-field-value-cr",
    ),
    IgnoredCase(
      id = "h2.client.reject-field-value-leading-space",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-field-value-leading-space",
    ),
    IgnoredCase(
      id = "h2.client.reject-field-value-leading-tab",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-field-value-leading-tab",
    ),
    IgnoredCase(
      id = "h2.client.reject-field-value-lf",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-field-value-lf",
    ),
    IgnoredCase(
      id = "h2.client.reject-field-value-nul",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-field-value-nul",
    ),
    IgnoredCase(
      id = "h2.client.reject-field-value-trailing-space",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-field-value-trailing-space",
    ),
    IgnoredCase(
      id = "h2.client.reject-field-value-trailing-tab",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-field-value-trailing-tab",
    ),
    IgnoredCase(
      id = "h2.client.reject-headers-padding-equals-payload",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); COMPRESSION_ERROR for invalid padding requiring PROTOCOL_ERROR (generic INTERNAL_ERROR also allowed)",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-headers-padding-equals-payload",
    ),
    IgnoredCase(
      id = "h2.client.reject-headers-padding-exceeds-payload",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); COMPRESSION_ERROR for invalid padding requiring PROTOCOL_ERROR (generic INTERNAL_ERROR also allowed)",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-headers-padding-exceeds-payload",
    ),
    IgnoredCase(
      id = "h2.client.reject-headers-stream-zero",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-headers-stream-zero",
    ),
    IgnoredCase(
      id = "h2.client.reject-informational-end-stream",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-informational-end-stream",
    ),
    IgnoredCase(
      id = "h2.client.reject-keep-alive-field",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-keep-alive-field",
    ),
    IgnoredCase(
      id = "h2.client.reject-preface-first-ping",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-preface-first-ping",
    ),
    IgnoredCase(
      id = "h2.client.reject-priority-stream-zero",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-priority-stream-zero",
    ),
    IgnoredCase(
      id = "h2.client.reject-proxy-connection-field",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-proxy-connection-field",
    ),
    IgnoredCase(
      id = "h2.client.reject-pseudo-header-after-regular-field",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking =
        "docs/wire-tck/known-failures.md#h2-client-reject-pseudo-header-after-regular-field",
    ),
    IgnoredCase(
      id = "h2.client.reject-push-invalid-field",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-push-invalid-field",
    ),
    IgnoredCase(
      id = "h2.client.reject-push-missing-method",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-push-missing-method",
    ),
    IgnoredCase(
      id = "h2.client.reject-push-request-content",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-push-request-content",
    ),
    IgnoredCase(
      id = "h2.client.reject-push-stream-zero",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-push-stream-zero",
    ),
    IgnoredCase(
      id = "h2.client.reject-push-unauthoritative-origin",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-push-unauthoritative-origin",
    ),
    IgnoredCase(
      id = "h2.client.reject-push-unsafe-method",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-push-unsafe-method",
    ),
    IgnoredCase(
      id = "h2.client.reject-request-authority-pseudo-header",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-request-authority-pseudo-header",
    ),
    IgnoredCase(
      id = "h2.client.reject-request-method-pseudo-header",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-request-method-pseudo-header",
    ),
    IgnoredCase(
      id = "h2.client.reject-request-path-pseudo-header",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-request-path-pseudo-header",
    ),
    IgnoredCase(
      id = "h2.client.reject-request-scheme-pseudo-header",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-request-scheme-pseudo-header",
    ),
    IgnoredCase(
      id = "h2.client.reject-response-te",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-response-te",
    ),
    IgnoredCase(
      id = "h2.client.reject-settings-server-enable-push-one",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-settings-server-enable-push-one",
    ),
    IgnoredCase(
      id = "h2.client.reject-switching-protocols",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST NOT); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-switching-protocols",
    ),
    IgnoredCase(
      id = "h2.client.reject-trailer-prohibited-field",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-trailer-prohibited-field",
    ),
    IgnoredCase(
      id = "h2.client.reject-trailer-pseudo-header",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-trailer-pseudo-header",
    ),
    IgnoredCase(
      id = "h2.client.reject-trailer-uppercase-name",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-trailer-uppercase-name",
    ),
    IgnoredCase(
      id = "h2.client.reject-trailers-without-end-stream",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response; native: confirmed mandatory deviation (MUST/MUST NOT); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-trailers-without-end-stream",
    ),
    IgnoredCase(
      id = "h2.client.reject-transfer-encoding-field",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-transfer-encoding-field",
    ),
    IgnoredCase(
      id = "h2.client.reject-undefined-pseudo-header",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-undefined-pseudo-header",
    ),
    IgnoredCase(
      id = "h2.client.reject-upgrade-field",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-upgrade-field",
    ),
    IgnoredCase(
      id = "h2.client.reject-uppercase-field-name",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-reject-uppercase-field-name",
    ),
    IgnoredCase(
      id = "h2.client.settings-stream-window-overflow",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-client-settings-stream-window-overflow",
    ),
    IgnoredCase(
      id = "h2.client.settings-window-maximum",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); FLOW_CONTROL_ERROR rejects legal INITIAL_WINDOW_SIZE=2147483647",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-settings-window-maximum",
    ),
    IgnoredCase(
      id = "h2.client.unpromised-server-headers",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-client-unpromised-server-headers",
    ),
    IgnoredCase(
      id = "h2.client.validate-field-empty-name",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (conditional MUST); COMPRESSION_ERROR for detected HTTP field-name error in structurally valid HPACK",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-validate-field-empty-name",
    ),
    IgnoredCase(
      id = "h2.client.validate-field-separator-name",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed recommendation deviation (SHOULD; not MUST); the client consumed a deliberately malformed response",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-client-validate-field-separator-name",
    ),
    IgnoredCase(
      id = "h2.server.connection-credit-independent",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST NOT); DATA payload 16384 exceeds remaining connection credit 16383 before completion grants",
      revision = finalComparisonEvidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-server-connection-credit-independent",
    ),
    IgnoredCase(
      id = "h2.server.hpack-encoder-minimum-before-restore",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); invalid HPACK index after acknowledged encoder table reduction",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-server-hpack-encoder-minimum-before-restore",
    ),
    IgnoredCase(
      id = "h2.server.hpack-encoder-reduction-zero",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); invalid HPACK index after acknowledged encoder table reduction",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-server-hpack-encoder-reduction-zero",
    ),
    IgnoredCase(
      id = "h2.server.hpack-reject-huffman-name-padding-eight",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-hpack-reject-huffman-name-padding-eight",
    ),
    IgnoredCase(
      id = "h2.server.hpack-reject-huffman-padding-eight",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-hpack-reject-huffman-padding-eight",
    ),
    IgnoredCase(
      id = "h2.server.hpack-reject-huffman-string-truncated",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-hpack-reject-huffman-string-truncated",
    ),
    IgnoredCase(
      id = "h2.server.hpack-reject-incremental-name-integer-truncated",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking =
        "docs/wire-tck/known-failures.md#h2-server-hpack-reject-incremental-name-integer-truncated",
    ),
    IgnoredCase(
      id = "h2.server.hpack-reject-index-integer-truncated",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-hpack-reject-index-integer-truncated",
    ),
    IgnoredCase(
      id = "h2.server.hpack-reject-name-length-truncated",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-hpack-reject-name-length-truncated",
    ),
    IgnoredCase(
      id = "h2.server.hpack-reject-name-truncated",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-hpack-reject-name-truncated",
    ),
    IgnoredCase(
      id = "h2.server.hpack-reject-never-name-integer-truncated",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking =
        "docs/wire-tck/known-failures.md#h2-server-hpack-reject-never-name-integer-truncated",
    ),
    IgnoredCase(
      id = "h2.server.hpack-reject-string-length-truncated",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-hpack-reject-string-length-truncated",
    ),
    IgnoredCase(
      id = "h2.server.hpack-reject-update-after-index",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-hpack-reject-update-after-index",
    ),
    IgnoredCase(
      id = "h2.server.hpack-reject-update-after-literal",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-hpack-reject-update-after-literal",
    ),
    IgnoredCase(
      id = "h2.server.hpack-reject-value-missing",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-hpack-reject-value-missing",
    ),
    IgnoredCase(
      id = "h2.server.hpack-reject-value-truncated",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-hpack-reject-value-truncated",
    ),
    IgnoredCase(
      id = "h2.server.hpack-reject-without-name-integer-truncated",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking =
        "docs/wire-tck/known-failures.md#h2-server-hpack-reject-without-name-integer-truncated",
    ),
    IgnoredCase(
      id = "h2.server.message-empty-trailers",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (syntax/MUST); COMPRESSION_ERROR rejects valid empty trailing HEADERS",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-server-message-empty-trailers",
    ),
    IgnoredCase(
      id = "h2.server.reject-authority-userinfo",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-authority-userinfo",
    ),
    IgnoredCase(
      id = "h2.server.reject-field-name-colon",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-field-name-colon",
    ),
    IgnoredCase(
      id = "h2.server.reject-field-name-control-boundary",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-field-name-control-boundary",
    ),
    IgnoredCase(
      id = "h2.server.reject-field-name-del",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-field-name-del",
    ),
    IgnoredCase(
      id = "h2.server.reject-field-name-nul",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-field-name-nul",
    ),
    IgnoredCase(
      id = "h2.server.reject-field-name-obs-text-high",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-field-name-obs-text-high",
    ),
    IgnoredCase(
      id = "h2.server.reject-field-name-obs-text-low",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-field-name-obs-text-low",
    ),
    IgnoredCase(
      id = "h2.server.reject-field-name-space",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-field-name-space",
    ),
    IgnoredCase(
      id = "h2.server.reject-field-name-tab",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-field-name-tab",
    ),
    IgnoredCase(
      id = "h2.server.reject-field-value-cr",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-field-value-cr",
    ),
    IgnoredCase(
      id = "h2.server.reject-field-value-leading-space",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-field-value-leading-space",
    ),
    IgnoredCase(
      id = "h2.server.reject-field-value-leading-tab",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-field-value-leading-tab",
    ),
    IgnoredCase(
      id = "h2.server.reject-field-value-lf",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-field-value-lf",
    ),
    IgnoredCase(
      id = "h2.server.reject-field-value-nul",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-field-value-nul",
    ),
    IgnoredCase(
      id = "h2.server.reject-field-value-trailing-space",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-field-value-trailing-space",
    ),
    IgnoredCase(
      id = "h2.server.reject-field-value-trailing-tab",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-field-value-trailing-tab",
    ),
    IgnoredCase(
      id = "h2.server.reject-headers-padding-equals-payload",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); COMPRESSION_ERROR for invalid padding requiring PROTOCOL_ERROR (generic INTERNAL_ERROR also allowed)",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-headers-padding-equals-payload",
    ),
    IgnoredCase(
      id = "h2.server.reject-headers-padding-exceeds-payload",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); COMPRESSION_ERROR for invalid padding requiring PROTOCOL_ERROR (generic INTERNAL_ERROR also allowed)",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-headers-padding-exceeds-payload",
    ),
    IgnoredCase(
      id = "h2.server.reject-keep-alive-field",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-keep-alive-field",
    ),
    IgnoredCase(
      id = "h2.server.reject-path-fragment",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-path-fragment",
    ),
    IgnoredCase(
      id = "h2.server.reject-preface-first-ping",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-preface-first-ping",
    ),
    IgnoredCase(
      id = "h2.server.reject-priority-stream-zero",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-priority-stream-zero",
    ),
    IgnoredCase(
      id = "h2.server.reject-proxy-connection-field",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-proxy-connection-field",
    ),
    IgnoredCase(
      id = "h2.server.reject-relative-path",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-relative-path",
    ),
    IgnoredCase(
      id = "h2.server.reject-repeated-authority",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-repeated-authority",
    ),
    IgnoredCase(
      id = "h2.server.reject-trailer-prohibited-field",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-trailer-prohibited-field",
    ),
    IgnoredCase(
      id = "h2.server.reject-trailer-pseudo-header",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-trailer-pseudo-header",
    ),
    IgnoredCase(
      id = "h2.server.reject-trailer-uppercase-name",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-trailer-uppercase-name",
    ),
    IgnoredCase(
      id = "h2.server.reject-trailers-without-end-stream",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-trailers-without-end-stream",
    ),
    IgnoredCase(
      id = "h2.server.reject-transfer-encoding-field",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-transfer-encoding-field",
    ),
    IgnoredCase(
      id = "h2.server.reject-upgrade-field",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-reject-upgrade-field",
    ),
    IgnoredCase(
      id = "h2.server.settings-stream-window-overflow",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-settings-stream-window-overflow",
    ),
    IgnoredCase(
      id = "h2.server.settings-window-maximum",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (MUST); FLOW_CONTROL_ERROR rejects legal INITIAL_WINDOW_SIZE=2147483647",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-server-settings-window-maximum",
    ),
    IgnoredCase(
      id = "h2.server.validate-field-empty-name",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: confirmed mandatory deviation (conditional MUST); COMPRESSION_ERROR for detected HTTP field-name error in structurally valid HPACK",
      revision = phase3A4Evidence,
      status = "confirmed",
      tracking = "docs/wire-tck/known-failures.md#h2-server-validate-field-empty-name",
    ),
    IgnoredCase(
      id = "h2.server.validate-field-separator-name",
      platforms = Set("js", "jvm", "native"),
      observed =
        "js/jvm/native: provisional observation limit (SHOULD; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
      revision = phase3A4Evidence,
      status = "provisional",
      tracking = "docs/wire-tck/known-failures.md#h2-server-validate-field-separator-name",
    ),
  )

  val all: Vector[IgnoredCase] = http1 ++ http2

  def get(id: String, platform: String): Option[IgnoredCase] =
    all.find(e => e.id == id && e.platforms(platform))

  def validate(cases: Vector[WireCase]): Vector[String] = {
    val ids = cases.map(_.id)
    val duplicateCases = ids
      .groupBy(identity)
      .collect {
        case (id, xs) if xs.size > 1 =>
          s"duplicate case: $id"
      }
      .toVector
    val duplicateIgnores = all
      .groupBy(_.id)
      .collect {
        case (id, xs) if xs.size > 1 =>
          s"duplicate ignore: $id"
      }
      .toVector
    duplicateCases ++ duplicateIgnores ++ all.flatMap { e =>
      Vector(
        if (!ids.contains(e.id)) Some(s"stale ignore: ${e.id}") else None,
        if (e.platforms.isEmpty || !e.platforms.subsetOf(Set("jvm", "js", "native")))
          Some(s"invalid platforms: ${e.id}")
        else None,
        if (Vector(e.observed, e.revision, e.status, e.tracking).exists(_.trim.isEmpty))
          Some(s"incomplete ignore: ${e.id}")
        else None,
        if (!Set("provisional", "confirmed")(e.status))
          Some(s"invalid review status: ${e.id}")
        else None,
        if (!e.tracking.startsWith("docs/wire-tck/known-failures.md#"))
          Some(s"missing local tracking reference: ${e.id}")
        else None,
      ).flatten
    } ++ http1.filterNot(_.id.startsWith("h1.")).map(e => s"wrong registry section: ${e.id}") ++
      http2.filterNot(_.id.startsWith("h2.")).map(e => s"wrong registry section: ${e.id}") ++ cases
        .collect {
          case c if c.references.isEmpty || c.references.exists(_.requirement.trim.isEmpty) =>
            s"missing specification reference: ${c.id}"
        }
  }
}
