# Frozen target contract and authoring rules

The phase-1 `Model.scala` target contract is shared by all targets and platforms.
New cases must not branch on `target.name` or `RuntimeInfo.platform`, normalize
wire stimuli through a production HTTP codec, or add local suppression tags.
Coordinates for authoritative RFC TXT/HTML are in [baseline.md](baseline.md).

Use `WireCase(id, protocol, role, Rfc(number, section, requirement, strength)) { ctx => ... }`.
Every ID matches `h[12].(client|server).[a-z0-9-]+` and remains stable. Every
concrete table variant becomes a separate `WireCase`. Put a direct RFC section
URL comment beside each case; show setup, stimulus and assertions in its body.
Strength is `MUST` by default; use `MUST NOT`, `SHOULD`, `MAY` or `syntax` when
appropriate. Recommendations and permission alternatives must not become invented
mandatory rejection requirements.

`ctx.server(protocol, scenario).use { case (peer, server) => ... }` owns an isolated
target listener/connection and raw peer. Scenarios are Reply (status, ordered raw
headers, binary body), EchoBody, EchoHeaders, and ReplyChunks. `server.received`
awaits one application observation; `pollReceived` consumes an available observation
without waiting. EchoBody waits for its complete body; other scenarios observe
request heads. `RequestData.path` observes the HTTP/1 request-target or HTTP/2's
:path component including its query; HTTP/2 scheme and authority are separate
components, not a prefix to this observation. A head observation does not prove an invalid body was accepted.

`ctx.client(protocol)(driver => ...)(peer => ...)` owns a raw listening peer and
target client resource. It keeps the peer connection open until target operations
finish; signal intended EOF explicitly with `peer.endOfOutput`. Driver `exchange`
and `exchangeStream` return resource-scoped responses with status, ordered fields,
body stream, and trailers. Attempt only the target operation when asserting
rejection; errors in the scripted peer must still fail the test. Deadlines sit
outside attempts. Actual IO cancellation does not turn into successful rejection.

RawPeer supports exact writes, ASCII writes, exact reads, delimiter reads, bounded
EOF reads and half-close. Http1Wire independently reads a request, one response,
or a final response while preserving binary bodies and field occurrences.
Http2Wire constructs and reads explicit frames (including malformed declared
lengths), settings, headers, data, window updates, and bounded frame sequences.
`readUntil` handles ordinary control frames; do not use it where ignoring any of
those controls could conceal the rule being tested. Standard `serverHandshake`
reads the complete client preface/settings before sending its own settings.
Eager-preface sequencing needs an explicit script. HPACK literal encoding is for
valid stimuli; raw hex can intentionally violate representation rules.

HTTP/2 cases allocate a private `Http2PeerSettings` scope and propagate it through
all frame readers and scripted writes. It retains observed settings separately for
each connection. Admission checks count a request stream until both directions
end or either direction resets it, so successive completed requests can reuse one
slot. Existing streams can continue after a concurrency reduction. Stream-zero,
even, reused and decreasing-ID fault stimuli are not treated as fresh admission.

`MAX_HEADER_LIST_SIZE` is advisory: sending more is legal, and rejecting or
discarding an oversized section is also legal. Acceptance fixtures therefore
require the actual section to fit the advertised limit. The independent outbound
HPACK observer counts decoded wire octets of each name/value plus 32 per occurrence
(including repeated fields and every UTF-8 byte), for HEADERS, trailers and
PUSH_PROMISE. Use `Http2PeerSettings.writeFrames` for a planned fragmented section,
and its `write` helper for finite encoded byte sequences, so prerequisites are
checked before any section bytes. Acceptance scenarios with a finite advisory
limit and an undecodable or separately fragmented block report `WireObservationLimit`.
Framing-negative cases with independently known logical fields use
`Http2PeerSettings.writeFramingFault`: its explicit octet-field witness must fit the
current limit before the unchanged faulty frames are sent, with admission still checked.
Write positive warmups separately through the ordinary checked path; malformed
empty trailers have an empty witness. This does not infer a bound from arbitrary
invalid bytes or exempt unknown sections.
A dedicated compression-negative fault block explicitly sets `requireFieldAcceptance = false`:
RFC9113 4.3 requires decompression even when fields are discarded. Its malformed
bytes must reach the target; any positive warmup block retains acceptance checks.
Separately written valid continuations retain the connection's outbound compression state.

Dedicated HPACK sessions retain the minimum table limit since the last block as
well as the final value. If honoring a new minimum would remove dynamic state
needed for a witness, stop with a typed prerequisite before its next block. Do not
silently rebuild the state being tested. An initial-empty-table witness must also
stop when initial SETTINGS would require a clearing update: that update could hide
a faulty prepopulated decoder. These prerequisite errors leave coverage
unexercised; they establish no additional sender MUST or target defect.

Check.equal and Check(condition, detail) produce ProtocolFailure. Unexpected
fixture exceptions block comparison; timeout produces an inconclusive outcome,
never a pass. Each case has an eight-second deadline and a bounded transcript.
Do not use sleep to infer absence of bytes. For flow-control silence, establish
an independent liveness barrier (such as an acknowledged PING), inspect every
intervening frame, then release credit and prove progress. Document unavoidable
observation limits.

## Requirements inventory format

Phase-2 workers own disjoint TSV fragments under `docs/wire-tck/inventory/`.
Columns: `id`, `rfc`, `section`, `role`, `strength`, `requirement`, `disposition`,
`cases`, `notes`. Separate fields with tabs; lists of case IDs use commas. Each row
represents a specific obligation, exception, grammar production, state rule or
cross-reference. Disposition is `covered`, `gap` or `excluded`. Covered rows name
executable cases; gaps describe missing observations/variants; exclusions justify
the scope decision. Derive these rows from the RFC independently of what cases
happen to exist. Broad section headings are an index, not obligation coverage.

Each author owns only assigned case and inventory files. The coordinator owns
Catalog.scala, shared machinery, adapters, registry integration, validation and
commits. Request contract changes before editing shared infrastructure.

Origin servers may process requests progressively (RFC 9113 section 8.1.1), and
HPACK decoders may expose decoded fields before completing a block (RFC 7541
section 3.1). Such application observations alone establish neither acceptance
nor rejection; require the applicable wire error. An incidental push-stream reset
cannot satisfy a required connection verdict: those branches continue observing
the independently required connection termination.

Connection-control errors can coexist with delivery of an otherwise valid response.
The 21 connection-control fixtures hold successful exchange resources through their
independent wire verdict, so exchange cleanup cannot manufacture EOF. They preserve
adapter, timeout and oracle errors. This does not relax malformed-response,
stream-error PRIORITY or no-response assertions. The five GOAWAY closure-choice
fixtures permit both generic error codes under RFC9113 5.4 while retaining checks
on any delivered response.
