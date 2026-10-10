# Known Ember protocol observations

Generated audit and validation artifacts are not version-controlled and have been removed from this checkout. Artifact names and original revision IDs below are historical references, not available files or revisions in the rewritten branch. Run `sbt --client tckCheck` and `sbt --client tckCompare` to generate fresh results under `target/wire-tck-reports/`.

Exact entries in `IgnoreRegistry.scala` suppress ordinary runs only. Every
nonpassing Ember protocol case in the complete phase-2 run is recorded below,
including provisional assertions, recommendations and inconclusive deadlines.
These are observations awaiting independent phase-3 review, not a list of
confirmed mandatory protocol defects. Comparison always executes them.

Evidence: all 3,008 outcomes and bounded wire transcripts (`validation/phase2-2.13.18.zip`, historical artifact).
Catalog evidence revision: `2d3a58784d510279cd56d1d11f5c500a57f05e6e:b4a41bd2cb227e053e8a4539e3c4973fdef39aa9bad8d54ffb54ee55ee62777e`.

160 distinct case IDs are registered. Affected platforms are listed individually.

## h1-client-chunk-extension-bad-whitespace

Case: `h1.client.chunk-extension-bad-whitespace`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Recipients parse the BWS positions defined by the chunk extension grammar.](https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1.1).

```text
org.http4s.wire.ProtocolFailure: the target rejected a valid response: org.http4s.ember.core.EmberException$ChunkedEncodingError: Failed to parse chunked header : Right(3 	; 	wire-token 	= 	alpha
)
```

## h1-client-error-response-during-upload

Case: `h1.client.error-response-during-upload`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: SHOULD. [A client monitors responses during upload and stops sending when an error closes the connection.](https://www.rfc-editor.org/rfc/rfc9112.html#section-9.5).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

This recommendation is not classified as a MUST-level conformance failure.

## h1-client-generated-asterisk-form

Case: `h1.client.generated-asterisk-form`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A server-wide OPTIONS request uses only an asterisk as its request-target.](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2.4).

```text
org.http4s.wire.ProtocolFailure: expected: *
observed: /*
```

Review the target URI representation and adapter fidelity before confirming this observation.

## h1-client-informational-before-final-response

Case: `h1.client.informational-before-final-response`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Informational responses belong to the outstanding request until its final response.](https://www.rfc-editor.org/rfc/rfc9112.html#section-9.2).

```text
org.http4s.wire.ProtocolFailure: expected: 200
observed: 100
```

Review must distinguish API exposure of interim responses from failure to parse or associate the final wire response.

## h1-client-informational-does-not-use-close-delimitation

Case: `h1.client.informational-does-not-use-close-delimitation`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [An informational response ends at its header boundary before the final response starts.](https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3).

```text
org.http4s.wire.ProtocolFailure: expected: 200
observed: 103
```

Review must distinguish API exposure of interim responses from failure to parse or associate the final wire response.

## h1-client-interaction-interim-next-request-association

Case: `h1.client.interaction-interim-next-request-association`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Interim and final responses to one request cannot be associated with a later request on the connection.](https://www.rfc-editor.org/rfc/rfc9112.html#section-9.2).

```text
org.http4s.wire.ProtocolFailure: expected: 201
observed: 103
```

## h1-client-multiple-informational-boundaries

Case: `h1.client.multiple-informational-boundaries`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Each informational response is bodyless and does not consume the next response.](https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3).

```text
org.http4s.wire.ProtocolFailure: expected: 200
observed: 100
```

Review must distinguish API exposure of interim responses from failure to parse or associate the final wire response.

## h1-client-obsolete-fold-multiple

Case: `h1.client.obsolete-fold-multiple`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A user agent must replace each obsolete response field fold with one or more spaces.](https://www.rfc-editor.org/rfc/rfc9112.html#section-5.2).

```text
org.http4s.wire.ProtocolFailure: the target rejected a response requiring acceptance: org.http4s.ember.core.Parser$HeaderP$ParseHeadersError: Encountered Error Attempting to Parse Headers - InvalidHeaderWhitespace
```

## h1-client-obsolete-fold-space

Case: `h1.client.obsolete-fold-space`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A user agent must replace each obsolete response field fold with one or more spaces.](https://www.rfc-editor.org/rfc/rfc9112.html#section-5.2).

```text
org.http4s.wire.ProtocolFailure: the target rejected a response requiring acceptance: org.http4s.ember.core.Parser$HeaderP$ParseHeadersError: Encountered Error Attempting to Parse Headers - InvalidHeaderWhitespace
```

## h1-client-obsolete-fold-tab

Case: `h1.client.obsolete-fold-tab`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A user agent must replace each obsolete response field fold with one or more spaces.](https://www.rfc-editor.org/rfc/rfc9112.html#section-5.2).

```text
org.http4s.wire.ProtocolFailure: the target rejected a response requiring acceptance: org.http4s.ember.core.Parser$HeaderP$ParseHeadersError: Encountered Error Attempting to Parse Headers - InvalidHeaderWhitespace
```

## h1-server-absolute-form-empty-host

Case: `h1.server.absolute-form-empty-host`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A recipient must reject an http URI with an empty host identifier.](https://www.rfc-editor.org/rfc/rfc9110.html#section-4.2.1).

```text
org.http4s.wire.ProtocolFailure: an empty http URI host was accepted: Some(200)
```

## h1-server-chunk-extension-bad-whitespace

Case: `h1.server.chunk-extension-bad-whitespace`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Recipients parse the BWS positions defined by the chunk extension grammar.](https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1.1).

```text
org.http4s.wire.ProtocolFailure: expected: 200
observed: 500
```

## h1-server-duplicate-host-case-insensitive

Case: `h1.server.duplicate-host-case-insensitive`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Multiple Host field lines or an invalid Host field value require a 400 response.](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2).

```text
org.http4s.wire.ProtocolFailure: expected: 400
observed: 200
```

## h1-server-duplicate-host-different

Case: `h1.server.duplicate-host-different`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Multiple Host field lines or an invalid Host field value require a 400 response.](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2).

```text
org.http4s.wire.ProtocolFailure: expected: 400
observed: 200
```

## h1-server-duplicate-host-identical

Case: `h1.server.duplicate-host-identical`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Multiple Host field lines or an invalid Host field value require a 400 response.](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2).

```text
org.http4s.wire.ProtocolFailure: expected: 400
observed: 200
```

## h1-server-field-name-delimiter

Case: `h1.server.field-name-delimiter`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: SHOULD. [Field names are tokens; malformed messages should receive 400 and connection closure.](https://www.rfc-editor.org/rfc/rfc9112.html#section-2.2).

```text
org.http4s.wire.ProtocolFailure: expected: 400
observed: 200
```

This recommendation is not classified as a MUST-level conformance failure.

## h1-server-head-response-has-no-content

Case: `h1.server.head-response-has-no-content`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [HEAD, 204, and 304 responses terminate at the end of headers and cannot send content or trailers.](https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3).

```text
org.http4s.wire.ProtocolFailure: expected: ByteVector(empty)
observed: ByteVector(3 bytes, 0x616263)
```

## h1-server-host-comma

Case: `h1.server.host-comma`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Multiple Host field lines or an invalid Host field value require a 400 response.](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2).

```text
org.http4s.wire.ProtocolFailure: expected: 400
observed: 200
```

## h1-server-host-nondigit-port

Case: `h1.server.host-nondigit-port`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Multiple Host field lines or an invalid Host field value require a 400 response.](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2).

```text
org.http4s.wire.ProtocolFailure: expected: 400
observed: 200
```

## h1-server-host-path

Case: `h1.server.host-path`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Multiple Host field lines or an invalid Host field value require a 400 response.](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2).

```text
org.http4s.wire.ProtocolFailure: expected: 400
observed: 200
```

## h1-server-host-space

Case: `h1.server.host-space`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Multiple Host field lines or an invalid Host field value require a 400 response.](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2).

```text
org.http4s.wire.ProtocolFailure: expected: 400
observed: 200
```

## h1-server-host-unclosed-ipv6-literal

Case: `h1.server.host-unclosed-ipv6-literal`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [An invalid Host field value requires a 400 response.](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2).

```text
org.http4s.wire.ProtocolFailure: expected: 400
observed: 200
```

## h1-server-host-userinfo

Case: `h1.server.host-userinfo`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Multiple Host field lines or an invalid Host field value require a 400 response.](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2).

```text
org.http4s.wire.ProtocolFailure: expected: 400
observed: 200
```

## h1-server-http10-close-overrides-keep-alive

Case: `h1.server.http10-close-overrides-keep-alive`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [An HTTP/1.0 close option takes precedence over keep-alive.](https://www.rfc-editor.org/rfc/rfc9112.html#section-9.3).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

## h1-server-http10-response-has-no-transfer-encoding

Case: `h1.server.http10-response-has-no-transfer-encoding`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A server must not emit Transfer-Encoding when the corresponding request indicates HTTP/1.0.](https://www.rfc-editor.org/rfc/rfc9112.html#section-6.1).

```text
org.http4s.wire.ProtocolFailure: expected: Vector()
observed: Vector(chunked)
```

## h1-server-leading-empty-line

Case: `h1.server.leading-empty-line`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: SHOULD. [A server expecting a request-line should ignore at least one preceding empty line.](https://www.rfc-editor.org/rfc/rfc9112.html#section-2.2).

```text
org.http4s.wire.ProtocolFailure: expected: 200
observed: 400
```

This recommendation is not classified as a MUST-level conformance failure.

## h1-server-missing-host

Case: `h1.server.missing-host`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Reject an HTTP/1.1 request with no Host field using 400.](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2).

```text
org.http4s.wire.ProtocolFailure: expected: 400
observed: 200
```

## h1-server-no-content-response-has-no-content

Case: `h1.server.no-content-response-has-no-content`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [HEAD, 204, and 304 responses terminate at the end of headers and cannot send content or trailers.](https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3).

```text
org.http4s.wire.ProtocolFailure: expected: ByteVector(empty)
observed: ByteVector(3 bytes, 0x616263)
```

## h1-server-not-modified-response-has-no-content

Case: `h1.server.not-modified-response-has-no-content`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [HEAD, 204, and 304 responses terminate at the end of headers and cannot send content or trailers.](https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3).

```text
org.http4s.wire.ProtocolFailure: expected: ByteVector(empty)
observed: ByteVector(3 bytes, 0x616263)
```

## h1-server-unsupported-transfer-coding

Case: `h1.server.unsupported-transfer-coding`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: SHOULD. [An unknown request transfer coding should produce 501; a length-required policy may instead produce 411.](https://www.rfc-editor.org/rfc/rfc9112.html#section-6.1).

```text
org.http4s.wire.ProtocolFailure: expected unsupported coding or length-required policy, observed 400
```

This recommendation is not classified as a MUST-level conformance failure.

## h2-client-connection-credit-independent

Case: `h2.client.connection-credit-independent`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [SETTINGS and stream WINDOW_UPDATE cannot replenish exhausted upload connection credit.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.2).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

## h2-client-goaway-debug-data

Case: `h2.client.goaway-debug-data`. Review: provisional.

Platforms/outcomes: jvm fail; native intermittent failure in an earlier unchanged case. The latest Native execution passed.

Requirement strength: MUST. [GOAWAY may complete or interrupt an existing stream; debug bytes and unknown codes do not create a protocol error.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.8).

```text
org.http4s.wire.ProtocolFailure: valid GOAWAY elicited a protocol error: Frame(7,0,0,ByteVector(8 bytes, 0x0000000000000001))
```

Earlier Native evidence is retained in the intermittent observation record (`validation/phase2-intermittent-goaway.jsonl`, historical artifact), revision `2d3a58784d510279cd56d1d11f5c500a57f05e6e:bcea7bea27ec20cfb43d156450540d95992fb9ef6cd1c6e3450c0a199ad97118`. Retained provisionally until the independent review resolves this timing-dependent observation.

## h2-client-goaway-unknown-error

Case: `h2.client.goaway-unknown-error`. Review: provisional.

Platforms/outcomes: jvm fail; native intermittent failure in an earlier unchanged case. The latest Native execution passed.

Requirement strength: MUST. [GOAWAY may complete or interrupt an existing stream; debug bytes and unknown codes do not create a protocol error.](https://www.rfc-editor.org/rfc/rfc9113.html#section-7).

```text
org.http4s.wire.ProtocolFailure: valid GOAWAY elicited a protocol error: Frame(7,0,0,ByteVector(8 bytes, 0x0000000000000001))
```

Earlier Native evidence is retained in the intermittent observation record (`validation/phase2-intermittent-goaway.jsonl`, historical artifact), revision `2d3a58784d510279cd56d1d11f5c500a57f05e6e:bcea7bea27ec20cfb43d156450540d95992fb9ef6cd1c6e3450c0a199ad97118`. Retained provisionally until the independent review resolves this timing-dependent observation.

## h2-client-hpack-encoder-minimum-before-restore

Case: `h2.client.hpack-encoder-minimum-before-restore`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [If the peer limit falls to zero and rises before the next block, the encoder signals the minimum before any restored capacity.](https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2).

```text
org.http4s.wire.ProtocolFailure: target emitted invalid HPACK or framing: java.lang.IllegalArgumentException: invalid HPACK index
```

## h2-client-hpack-encoder-reduction-zero

Case: `h2.client.hpack-encoder-reduction-zero`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [An acknowledged reduction to zero is signaled at the beginning of the next block and no dynamic entries remain usable.](https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2).

```text
org.http4s.wire.ProtocolFailure: target emitted invalid HPACK or framing: java.lang.IllegalArgumentException: invalid HPACK index
```

## h2-client-hpack-reject-huffman-padding-eight

Case: `h2.client.hpack-reject-huffman-padding-eight`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Huffman padding longer than seven bits is a decoding error.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2).

```text
org.http4s.wire.ProtocolFailure: target client consumed malformed HPACK
```

## h2-client-hpack-reject-huffman-string-truncated

Case: `h2.client.hpack-reject-huffman-string-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A Huffman length cannot advertise more encoded octets than the field block contains.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2).

```text
org.http4s.wire.ProtocolFailure: target client consumed malformed HPACK
```

## h2-client-hpack-reject-incremental-name-integer-truncated

Case: `h2.client.hpack-reject-incremental-name-integer-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A saturated six-bit incremental name index requires its terminating continuation octet.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1).

```text
org.http4s.wire.ProtocolFailure: target client consumed malformed HPACK
```

## h2-client-hpack-reject-index-integer-truncated

Case: `h2.client.hpack-reject-index-integer-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A saturated indexed prefix requires its terminating continuation octet.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1).

```text
org.http4s.wire.ProtocolFailure: target client consumed malformed HPACK
```

## h2-client-hpack-reject-name-length-truncated

Case: `h2.client.hpack-reject-name-length-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A saturated literal name length prefix requires its terminating continuation octet.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1).

```text
org.http4s.wire.ProtocolFailure: target client consumed malformed HPACK
```

## h2-client-hpack-reject-name-truncated

Case: `h2.client.hpack-reject-name-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A literal name cannot extend beyond the completed field block.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2).

```text
org.http4s.wire.ProtocolFailure: target client consumed malformed HPACK
```

## h2-client-hpack-reject-never-name-integer-truncated

Case: `h2.client.hpack-reject-never-name-integer-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A saturated four-bit never-indexed name index requires its terminating continuation octet.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1).

```text
org.http4s.wire.ProtocolFailure: target client consumed malformed HPACK
```

## h2-client-hpack-reject-string-length-truncated

Case: `h2.client.hpack-reject-string-length-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A saturated string length prefix requires its terminating continuation octet.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1).

```text
org.http4s.wire.ProtocolFailure: target client consumed malformed HPACK
```

## h2-client-hpack-reject-update-after-index

Case: `h2.client.hpack-reject-update-after-index`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A dynamic table size update after an indexed field is a decoding error.](https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2).

```text
org.http4s.wire.ProtocolFailure: target client consumed malformed HPACK
```

## h2-client-hpack-reject-update-after-literal

Case: `h2.client.hpack-reject-update-after-literal`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A dynamic table size update after a literal field is a decoding error.](https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2).

```text
org.http4s.wire.ProtocolFailure: target client consumed malformed HPACK
```

## h2-client-hpack-reject-value-missing

Case: `h2.client.hpack-reject-value-missing`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A literal name must be followed by a complete value string representation.](https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.2).

```text
org.http4s.wire.ProtocolFailure: target client consumed malformed HPACK
```

## h2-client-hpack-reject-value-truncated

Case: `h2.client.hpack-reject-value-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A literal value cannot extend beyond the completed field block.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2).

```text
org.http4s.wire.ProtocolFailure: target client consumed malformed HPACK
```

## h2-client-hpack-reject-without-name-integer-truncated

Case: `h2.client.hpack-reject-without-name-integer-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A saturated four-bit non-indexing name index requires its terminating continuation octet.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1).

```text
org.http4s.wire.ProtocolFailure: target client consumed malformed HPACK
```

## h2-client-message-empty-trailers

Case: `h2.client.message-empty-trailers`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [An empty final trailer section can terminate a message after DATA.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1).

```text
org.http4s.wire.ProtocolFailure: the target rejected valid HTTP/2 input: java.util.concurrent.CancellationException: Received GoAway, cancelling: GoAway(identifier=0, lastStreamId=0, errorCode=CompressionError, additionalDebugData=None)
```

## h2-client-reject-connection-field

Case: `h2.client.reject-connection-field`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Connection-specific header fields must not occur in HTTP/2 messages.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-field-name-colon

Case: `h2.client.reject-field-name-colon`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-field-name-control-boundary

Case: `h2.client.reject-field-name-control-boundary`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-field-name-del

Case: `h2.client.reject-field-name-del`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-field-name-nul

Case: `h2.client.reject-field-name-nul`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-field-name-obs-text-high

Case: `h2.client.reject-field-name-obs-text-high`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-field-name-obs-text-low

Case: `h2.client.reject-field-name-obs-text-low`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-field-name-space

Case: `h2.client.reject-field-name-space`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-field-name-tab

Case: `h2.client.reject-field-name-tab`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-field-name-uppercase-boundary

Case: `h2.client.reject-field-name-uppercase-boundary`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-field-value-cr

Case: `h2.client.reject-field-value-cr`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A prohibited response field makes the message malformed and must not be accepted.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-field-value-leading-space

Case: `h2.client.reject-field-value-leading-space`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Field values must not begin or end with SP or HTAB.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-field-value-leading-tab

Case: `h2.client.reject-field-value-leading-tab`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Field values must not begin or end with SP or HTAB.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-field-value-lf

Case: `h2.client.reject-field-value-lf`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A prohibited response field makes the message malformed and must not be accepted.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-field-value-nul

Case: `h2.client.reject-field-value-nul`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A prohibited response field makes the message malformed and must not be accepted.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-field-value-trailing-space

Case: `h2.client.reject-field-value-trailing-space`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Field values must not begin or end with SP or HTAB.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-field-value-trailing-tab

Case: `h2.client.reject-field-value-trailing-tab`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Field values must not begin or end with SP or HTAB.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-headers-padding-equals-payload

Case: `h2.client.reject-headers-padding-equals-payload`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Invalid field-block framing requires a connection error; a stream reset alone does not satisfy this requirement.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2).

```text
org.http4s.wire.ProtocolFailure: incorrect GOAWAY error code
```

## h2-client-reject-headers-padding-exceeds-payload

Case: `h2.client.reject-headers-padding-exceeds-payload`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Invalid field-block framing requires a connection error; a stream reset alone does not satisfy this requirement.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2).

```text
org.http4s.wire.ProtocolFailure: incorrect GOAWAY error code
```

## h2-client-reject-headers-stream-zero

Case: `h2.client.reject-headers-stream-zero`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [Invalid field-block framing requires a connection error; a stream reset alone does not satisfy this requirement.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

## h2-client-reject-informational-end-stream

Case: `h2.client.reject-informational-end-stream`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [An informational HEADERS block with END_STREAM is malformed.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-keep-alive-field

Case: `h2.client.reject-keep-alive-field`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Connection-specific header fields must not occur in HTTP/2 messages.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-preface-first-ping

Case: `h2.client.reject-preface-first-ping`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [A server preface that starts with PING is a connection error.](https://www.rfc-editor.org/rfc/rfc9113.html#section-3.4).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

## h2-client-reject-priority-stream-zero

Case: `h2.client.reject-priority-stream-zero`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A PRIORITY frame on stream zero is a connection error.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.3).

```text
org.http4s.wire.ProtocolFailure: target completed an exchange despite a connection protocol error
```

## h2-client-reject-proxy-connection-field

Case: `h2.client.reject-proxy-connection-field`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Connection-specific header fields must not occur in HTTP/2 messages.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-pseudo-header-after-regular-field

Case: `h2.client.reject-pseudo-header-after-regular-field`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Every pseudo-header field must precede every regular field line.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-push-invalid-field

Case: `h2.client.reject-push-invalid-field`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [A promise must contain a valid request field section; acknowledged push disablement instead requires a connection error.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.4.1).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

## h2-client-reject-push-missing-method

Case: `h2.client.reject-push-missing-method`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [A promise must contain a complete request field section; acknowledged push disablement instead requires a connection error.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.4.1).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

## h2-client-reject-push-request-content

Case: `h2.client.reject-push-request-content`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A promise indicating request content must be rejected; acknowledged push disablement instead requires a connection error.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.4).

```text
org.http4s.wire.ProtocolFailure: incorrect RST_STREAM error code
```

## h2-client-reject-push-stream-zero

Case: `h2.client.reject-push-stream-zero`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [PUSH_PROMISE must be associated with a nonzero stream.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.6).

```text
org.http4s.wire.ProtocolFailure: incorrect RST_STREAM error code
```

## h2-client-reject-push-unauthoritative-origin

Case: `h2.client.reject-push-unauthoritative-origin`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A promise for an origin for which the peer is not authoritative must be rejected; acknowledged push disablement instead requires a connection error.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.4).

```text
org.http4s.wire.ProtocolFailure: incorrect RST_STREAM error code
```

## h2-client-reject-push-unsafe-method

Case: `h2.client.reject-push-unsafe-method`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [An unsafe promised request requires rejection of the promised stream; acknowledged push disablement instead requires a connection error.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.4).

```text
org.http4s.wire.ProtocolFailure: incorrect RST_STREAM error code
```

## h2-client-reject-request-authority-pseudo-header

Case: `h2.client.reject-request-authority-pseudo-header`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Request-only pseudo-header fields must not occur in responses.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-request-method-pseudo-header

Case: `h2.client.reject-request-method-pseudo-header`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Request-only pseudo-header fields must not occur in responses.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-request-path-pseudo-header

Case: `h2.client.reject-request-path-pseudo-header`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Request-only pseudo-header fields must not occur in responses.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-request-scheme-pseudo-header

Case: `h2.client.reject-request-scheme-pseudo-header`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Request-only pseudo-header fields must not occur in responses.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-response-te

Case: `h2.client.reject-response-te`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The TE exception is for requests; a response must not contain this connection-specific field.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-settings-server-enable-push-one

Case: `h2.client.reject-settings-server-enable-push-one`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Clients reject a server explicitly enabling push with SETTINGS_ENABLE_PUSH equal to one.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2).

```text
org.http4s.wire.ProtocolFailure: target completed an exchange despite a connection protocol error
```

## h2-client-reject-switching-protocols

Case: `h2.client.reject-switching-protocols`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [HTTP/2 does not support status 101 Switching Protocols.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.6).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-trailer-prohibited-field

Case: `h2.client.reject-trailer-prohibited-field`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A connection-specific field in a trailer section makes the message malformed.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-trailer-pseudo-header

Case: `h2.client.reject-trailer-pseudo-header`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Pseudo-header fields are prohibited in trailer sections.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-trailer-uppercase-name

Case: `h2.client.reject-trailer-uppercase-name`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Mandatory field validation applies to trailers as well as initial header sections.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-trailers-without-end-stream

Case: `h2.client.reject-trailers-without-end-stream`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A second field section after request headers or a final response must carry END_STREAM.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-transfer-encoding-field

Case: `h2.client.reject-transfer-encoding-field`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Connection-specific header fields must not occur in HTTP/2 messages.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-undefined-pseudo-header

Case: `h2.client.reject-undefined-pseudo-header`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Unnegotiated undefined pseudo-header fields make the message malformed.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-upgrade-field

Case: `h2.client.reject-upgrade-field`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Connection-specific header fields must not occur in HTTP/2 messages.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-reject-uppercase-field-name

Case: `h2.client.reject-uppercase-field-name`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A prohibited response field makes the message malformed and must not be accepted.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

## h2-client-settings-stream-window-overflow

Case: `h2.client.settings-stream-window-overflow`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [An initial-window SETTINGS change overflowing an upload window is a connection error.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.2).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

## h2-client-settings-window-maximum

Case: `h2.client.settings-window-maximum`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The defined setting accepts this unsigned boundary value and is acknowledged.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2).

```text
org.http4s.wire.ProtocolFailure: target rejected a valid control-frame exchange: java.util.concurrent.CancellationException: Received GoAway, cancelling: GoAway(identifier=0, lastStreamId=0, errorCode=FlowControlError, additionalDebugData=None)
```

```text
org.http4s.wire.ProtocolFailure: unexpected GOAWAY before the control barrier: Frame(7,0,0,ByteVector(8 bytes, 0x0000000000000003))
```

## h2-client-unpromised-server-headers

Case: `h2.client.unpromised-server-headers`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [HEADERS cannot open an unpromised server-initiated stream.](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.1).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

## h2-client-validate-field-empty-name

Case: `h2.client.validate-field-empty-name`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: SHOULD. [Full HTTP field-name token validation is recommended beyond the mandatory minimum character restrictions.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: incorrect GOAWAY error code
```

This recommendation is not classified as a MUST-level conformance failure.

## h2-client-validate-field-separator-name

Case: `h2.client.validate-field-separator-name`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: SHOULD. [Full HTTP field-name token validation is recommended beyond the mandatory minimum character restrictions.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

This recommendation is not classified as a MUST-level conformance failure.

## h2-server-connect-authority-form

Case: `h2.server.connect-authority-form`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A CONNECT request uses :method and host:port :authority, omitting :scheme and :path.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.5).

```text
org.http4s.wire.ProtocolFailure: expected: 1
observed: 3
```

## h2-server-connection-credit-independent

Case: `h2.server.connection-credit-independent`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Only connection WINDOW_UPDATE replenishes the initial 65535 connection octets.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.2).

```text
org.http4s.wire.ProtocolFailure: DATA payload 16384 exceeds remaining credit 16383
```

## h2-server-hpack-encoder-minimum-before-restore

Case: `h2.server.hpack-encoder-minimum-before-restore`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [If the peer limit falls to zero and rises before the next block, the encoder signals the minimum before any restored capacity.](https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2).

```text
org.http4s.wire.ProtocolFailure: target emitted invalid HPACK or framing: java.lang.IllegalArgumentException: invalid HPACK index
```

## h2-server-hpack-encoder-reduction-zero

Case: `h2.server.hpack-encoder-reduction-zero`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [An acknowledged reduction to zero is signaled at the beginning of the next block and no dynamic entries remain usable.](https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2).

```text
org.http4s.wire.ProtocolFailure: target emitted invalid HPACK or framing: java.lang.IllegalArgumentException: invalid HPACK index
```

## h2-server-hpack-reject-huffman-padding-eight

Case: `h2.server.hpack-reject-huffman-padding-eight`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Huffman padding longer than seven bits is a decoding error.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2).

```text
org.http4s.wire.ProtocolFailure: malformed HPACK reached the application
```

## h2-server-hpack-reject-huffman-string-truncated

Case: `h2.server.hpack-reject-huffman-string-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A Huffman length cannot advertise more encoded octets than the field block contains.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2).

```text
org.http4s.wire.ProtocolFailure: malformed HPACK reached the application
```

## h2-server-hpack-reject-incremental-name-integer-truncated

Case: `h2.server.hpack-reject-incremental-name-integer-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A saturated six-bit incremental name index requires its terminating continuation octet.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1).

```text
org.http4s.wire.ProtocolFailure: malformed HPACK reached the application
```

## h2-server-hpack-reject-index-integer-truncated

Case: `h2.server.hpack-reject-index-integer-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A saturated indexed prefix requires its terminating continuation octet.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1).

```text
org.http4s.wire.ProtocolFailure: malformed HPACK reached the application
```

## h2-server-hpack-reject-name-length-truncated

Case: `h2.server.hpack-reject-name-length-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A saturated literal name length prefix requires its terminating continuation octet.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1).

```text
org.http4s.wire.ProtocolFailure: malformed HPACK reached the application
```

## h2-server-hpack-reject-name-truncated

Case: `h2.server.hpack-reject-name-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A literal name cannot extend beyond the completed field block.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2).

```text
org.http4s.wire.ProtocolFailure: malformed HPACK reached the application
```

## h2-server-hpack-reject-never-name-integer-truncated

Case: `h2.server.hpack-reject-never-name-integer-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A saturated four-bit never-indexed name index requires its terminating continuation octet.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1).

```text
org.http4s.wire.ProtocolFailure: malformed HPACK reached the application
```

## h2-server-hpack-reject-string-length-truncated

Case: `h2.server.hpack-reject-string-length-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A saturated string length prefix requires its terminating continuation octet.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1).

```text
org.http4s.wire.ProtocolFailure: malformed HPACK reached the application
```

## h2-server-hpack-reject-update-after-index

Case: `h2.server.hpack-reject-update-after-index`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A dynamic table size update after an indexed field is a decoding error.](https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2).

```text
org.http4s.wire.ProtocolFailure: malformed HPACK reached the application
```

## h2-server-hpack-reject-update-after-literal

Case: `h2.server.hpack-reject-update-after-literal`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A dynamic table size update after a literal field is a decoding error.](https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2).

```text
org.http4s.wire.ProtocolFailure: malformed HPACK reached the application
```

## h2-server-hpack-reject-value-missing

Case: `h2.server.hpack-reject-value-missing`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A literal name must be followed by a complete value string representation.](https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.2).

```text
org.http4s.wire.ProtocolFailure: malformed HPACK reached the application
```

## h2-server-hpack-reject-value-truncated

Case: `h2.server.hpack-reject-value-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A literal value cannot extend beyond the completed field block.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2).

```text
org.http4s.wire.ProtocolFailure: malformed HPACK reached the application
```

## h2-server-hpack-reject-without-name-integer-truncated

Case: `h2.server.hpack-reject-without-name-integer-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A saturated four-bit non-indexing name index requires its terminating continuation octet.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1).

```text
org.http4s.wire.ProtocolFailure: malformed HPACK reached the application
```

## h2-server-maximum-stream-window-update

Case: `h2.server.maximum-stream-window-update`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [The maximum WINDOW_UPDATE increment is valid when the existing window is zero.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

## h2-server-message-empty-trailers

Case: `h2.server.message-empty-trailers`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [An empty final trailer section can terminate a message after DATA.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1).

```text
org.http4s.wire.ProtocolFailure: expected: 1
observed: 7
```

## h2-server-negative-stream-window

Case: `h2.server.negative-stream-window`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [A SETTINGS reduction preserves a negative stream window until credit becomes positive.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.2).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

## h2-server-reject-authority-userinfo

Case: `h2.server.reject-authority-userinfo`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A request pseudo-header value must obey its referenced method, URI, or path grammar.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector(),ByteVector(empty)))
```

## h2-server-reject-field-name-colon

Case: `h2.server.reject-field-name-colon`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((x:bad,ok)),ByteVector(empty)))
```

## h2-server-reject-field-name-control-boundary

Case: `h2.server.reject-field-name-control-boundary`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((xbad,ok)),ByteVector(empty)))
```

## h2-server-reject-field-name-del

Case: `h2.server.reject-field-name-del`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((xbad,ok)),ByteVector(empty)))
```

## h2-server-reject-field-name-nul

Case: `h2.server.reject-field-name-nul`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((x bad,ok)),ByteVector(empty)))
```

## h2-server-reject-field-name-obs-text-high

Case: `h2.server.reject-field-name-obs-text-high`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((xÿbad,ok)),ByteVector(empty)))
```

## h2-server-reject-field-name-obs-text-low

Case: `h2.server.reject-field-name-obs-text-low`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((xbad,ok)),ByteVector(empty)))
```

## h2-server-reject-field-name-space

Case: `h2.server.reject-field-name-space`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((x bad,ok)),ByteVector(empty)))
```

## h2-server-reject-field-name-tab

Case: `h2.server.reject-field-name-tab`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((x	bad,ok)),ByteVector(empty)))
```

## h2-server-reject-field-value-cr

Case: `h2.server.reject-field-value-cr`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A prohibited request field makes the message malformed and requires a stream error.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((x-value,a
b)),ByteVector(empty)))
```

## h2-server-reject-field-value-leading-space

Case: `h2.server.reject-field-value-leading-space`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Field values must not begin or end with SP or HTAB.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((x-value, value)),ByteVector(empty)))
```

## h2-server-reject-field-value-leading-tab

Case: `h2.server.reject-field-value-leading-tab`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Field values must not begin or end with SP or HTAB.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((x-value,	value)),ByteVector(empty)))
```

## h2-server-reject-field-value-lf

Case: `h2.server.reject-field-value-lf`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A prohibited request field makes the message malformed and requires a stream error.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((x-value,a
b)),ByteVector(empty)))
```

## h2-server-reject-field-value-nul

Case: `h2.server.reject-field-value-nul`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A prohibited request field makes the message malformed and requires a stream error.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((x-value,a b)),ByteVector(empty)))
```

## h2-server-reject-field-value-trailing-space

Case: `h2.server.reject-field-value-trailing-space`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Field values must not begin or end with SP or HTAB.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((x-value,value )),ByteVector(empty)))
```

## h2-server-reject-field-value-trailing-tab

Case: `h2.server.reject-field-value-trailing-tab`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Field values must not begin or end with SP or HTAB.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((x-value,value	)),ByteVector(empty)))
```

## h2-server-reject-headers-padding-equals-payload

Case: `h2.server.reject-headers-padding-equals-payload`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Invalid field-block framing requires a connection error; a stream reset alone does not satisfy this requirement.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2).

```text
org.http4s.wire.ProtocolFailure: incorrect GOAWAY error code
```

## h2-server-reject-headers-padding-exceeds-payload

Case: `h2.server.reject-headers-padding-exceeds-payload`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Invalid field-block framing requires a connection error; a stream reset alone does not satisfy this requirement.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2).

```text
org.http4s.wire.ProtocolFailure: incorrect GOAWAY error code
```

## h2-server-reject-keep-alive-field

Case: `h2.server.reject-keep-alive-field`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Connection-specific header fields must not occur in HTTP/2 messages.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((keep-alive,test)),ByteVector(empty)))
```

## h2-server-reject-path-fragment

Case: `h2.server.reject-path-fragment`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A request pseudo-header value must obey its referenced method, URI, or path grammar.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/path#fragment,Vector(),ByteVector(empty)))
```

## h2-server-reject-preface-first-ping

Case: `h2.server.reject-preface-first-ping`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [The client magic must be immediately followed by a SETTINGS frame.](https://www.rfc-editor.org/rfc/rfc9113.html#section-3.4).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

## h2-server-reject-priority-stream-zero

Case: `h2.server.reject-priority-stream-zero`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [A PRIORITY frame on stream zero is a connection error.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.3).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

## h2-server-reject-proxy-connection-field

Case: `h2.server.reject-proxy-connection-field`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Connection-specific header fields must not occur in HTTP/2 messages.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((proxy-connection,test)),ByteVector(empty)))
```

## h2-server-reject-relative-path

Case: `h2.server.reject-relative-path`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A request pseudo-header value must obey its referenced method, URI, or path grammar.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,relative,Vector(),ByteVector(empty)))
```

## h2-server-reject-repeated-authority

Case: `h2.server.reject-repeated-authority`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The same pseudo-header field name must not occur more than once, even with identical values.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector(),ByteVector(empty)))
```

## h2-server-reject-trailer-prohibited-field

Case: `h2.server.reject-trailer-prohibited-field`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A connection-specific field in a trailer section makes the message malformed.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector(),ByteVector(5 bytes, 0x00017f80ff)))
```

## h2-server-reject-trailer-pseudo-header

Case: `h2.server.reject-trailer-pseudo-header`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Pseudo-header fields are prohibited in trailer sections.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector(),ByteVector(5 bytes, 0x00017f80ff)))
```

## h2-server-reject-trailer-uppercase-name

Case: `h2.server.reject-trailer-uppercase-name`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Mandatory field validation applies to trailers as well as initial header sections.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector(),ByteVector(5 bytes, 0x00017f80ff)))
```

## h2-server-reject-trailers-without-end-stream

Case: `h2.server.reject-trailers-without-end-stream`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A second field section after request headers or a final response must carry END_STREAM.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector(),ByteVector(5 bytes, 0x00017f80ff)))
```

## h2-server-reject-transfer-encoding-field

Case: `h2.server.reject-transfer-encoding-field`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Connection-specific header fields must not occur in HTTP/2 messages.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((transfer-encoding,chunked)),ByteVector(empty)))
```

## h2-server-reject-upgrade-field

Case: `h2.server.reject-upgrade-field`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Connection-specific header fields must not occur in HTTP/2 messages.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((upgrade,test)),ByteVector(empty)))
```

## h2-server-request-split-cookie

Case: `h2.server.request-split-cookie`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Split Cookie field lines must be joined with semicolon and space before delivery to a generic application.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.3).

```text
org.http4s.wire.ProtocolFailure: expected: Vector(a=b; c=d; e=f)
observed: Vector(a=b, c=d, e=f)
```

## h2-server-reset-closes-blocked-stream

Case: `h2.server.reset-closes-blocked-stream`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [After processing RST_STREAM a server cannot resume DATA on that stream.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.4).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

## h2-server-settings-stream-window-overflow

Case: `h2.server.settings-stream-window-overflow`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [An initial-window SETTINGS change overflowing an active window is a connection error.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.2).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

## h2-server-settings-window-maximum

Case: `h2.server.settings-window-maximum`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The defined setting accepts this unsigned boundary value and is acknowledged.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2).

```text
org.http4s.wire.ProtocolFailure: unexpected GOAWAY before the control barrier: Frame(7,0,0,ByteVector(8 bytes, 0x0000000000000003))
```

## h2-server-stream-credit-boundary

Case: `h2.server.stream-credit-boundary`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [Each DATA payload fits the remaining stream credit and WINDOW_UPDATE restores credit.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.1).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

## h2-server-validate-field-empty-name

Case: `h2.server.validate-field-empty-name`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: SHOULD. [Full HTTP field-name token validation is recommended beyond the mandatory minimum character restrictions.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: incorrect GOAWAY error code
```

This recommendation is not classified as a MUST-level conformance failure.

## h2-server-validate-field-separator-name

Case: `h2.server.validate-field-separator-name`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: SHOULD. [Full HTTP field-name token validation is recommended beyond the mandatory minimum character restrictions.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((x/bad,value)),ByteVector(empty)))
```

This recommendation is not classified as a MUST-level conformance failure.

## h2-server-window-update-after-peer-end

Case: `h2.server.window-update-after-peer-end`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [WINDOW_UPDATE remains valid after the peer sends END_STREAM.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

## Lifecycle observation outside the wire registry

A JS-only fixture stress test canceled an infinite HTTP/2 upload to an Ember
EchoBody server. The upload source finalized, exchange fiber joined as canceled,
and the client resource released; releasing the Ember server then exceeded the
20-second test deadline. This is an observed server cleanup limitation, not a
wire-conformance classification. No production fix is included. The client
cancellation oracle instead uses an independent raw peer and observes transport
closure; other lifecycle tests retain Ember server release coverage. Cases must
terminate or deliberately reject their request streams before releasing an EchoBody
server. This limits what this fixture currently demonstrates about abandoned uploads.
