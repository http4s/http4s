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
