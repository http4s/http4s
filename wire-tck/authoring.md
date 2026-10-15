# Authoring wire tests

Each case runs through the same target contract on every supported implementation
and platform. Keep stimuli and assertions independent of `target.name` and
`RuntimeInfo.platform`, and construct wire traffic without production HTTP codecs.
The [specification baseline](baseline.md) defines which requirements belong here.

## Cases and requirements

Define each case as a named `WireCase` value and register it in the catalog. Use a
stable ID matching `h[12].(client|server).[a-z0-9-]+`, a nearby RFC section link,
and `Rfc` metadata describing the requirement and its strength. Each concrete
variant gets its own case. The body should make setup, stimulus and assertions
visible together.

Derive assertions from the requirement, including its permitted alternatives.
A sender prohibition does not by itself require receiver rejection, and a
recommendation must not become a mandatory assertion. `Check.equal` and `Check`
produce `ProtocolFailure`; unexpected fixture errors remain infrastructure
failures. A timeout is inconclusive, and cancellation is not successful rejection.

Add the requirement to
[SpecInventory](../wire-testkit/shared/src/main/scala/org/http4s/wire/SpecInventory.scala)
using direct references to the named cases. Mark missing observations as `gap`
and scope boundaries as `excluded`, with an explanation. Derive the inventory
from the RFC independently of the cases that happen to exist.
[SpecSections](../wire-testkit/shared/src/main/scala/org/http4s/wire/SpecSections.scala)
and [SpecErrata](../wire-testkit/shared/src/main/scala/org/http4s/wire/SpecErrata.scala)
record section classifications and errata decisions.

Register implementation failures in
[IgnoreRegistry](../wire-testkit/shared/src/main/scala/org/http4s/wire/IgnoreRegistry.scala),
with the affected platforms and observed behavior. Local suppression tags bypass
that registry and are forbidden. Repair test defects in the case itself.

## Resources and observations

`ctx.server(protocol, scenario).use { case (peer, server) => ... }` owns the target
listener, connection and raw peer. Choose a `ServerScenario` that makes application
behavior explicit. `server.received` waits for an application observation;
`pollReceived` checks without waiting. Only `EchoBody` waits for the complete body.
Observing a request head therefore does not establish acceptance of its body.
`RequestData.path` contains the HTTP/1 request-target or HTTP/2 `:path`, including
the query; HTTP/2 scheme and authority are separate fields.

`ctx.client(protocol)(driver => ...)(peer => ...)` owns the target client and a
listening raw peer. The peer remains open until target operations finish; signal
intended EOF with `peer.endOfOutput`. Driver responses are resource-scoped. When
asserting rejection, apply `.attempt` only to the target operation, leaving peer
errors and deadlines outside it. For connection-error assertions, hold response
resources through the wire verdict so response cleanup cannot manufacture EOF.

Use `Http1Wire`, `Http2Wire` and `HpackWire` to construct and inspect traffic while
preserving field occurrences and binary bodies. TCP write boundaries do not imply
read boundaries. HTTP/2 `readUntil` handles ordinary control frames; use an explicit
reader when ignoring an intervening frame could conceal the behavior under test.
The standard handshake reads the client preface and settings before responding;
cases testing eager prefaces need their own sequence.

Application delivery can precede a protocol error, so it cannot alone establish
acceptance or rejection. Assert the required wire outcome and its scope: a stream
reset cannot satisfy a required connection error. To test flow-control silence,
use a liveness barrier such as an acknowledged PING, inspect intervening frames,
then release credit and prove progress. Sleeping does not establish absence.

## HTTP/2 prerequisites

Allocate a private `Http2PeerSettings` scope and propagate it through frame readers
and scripted writes. It tracks each connection's settings and stream admission.
A request occupies a slot until both directions end or it is reset; existing
streams can continue after a concurrency reduction. Invalid stream IDs are fault
stimuli, not fresh admissions. Codec-only fixtures may explicitly use `untracked`,
which cannot create positive observers.

Acceptance fixtures must fit the peer's advertised `MAX_HEADER_LIST_SIZE`, since
a peer may discard larger sections. The outbound HPACK observer counts decoded
name and value octets plus 32 per occurrence. Use `writeFrames` for planned
fragmented sections and `write` for finite encoded bytes, so prerequisites are
checked before transmission. An undecodable or separately fragmented block under
a finite limit may produce `WireObservationLimit`.

For framing faults, use `writeFramingFault` with an explicit logical-field witness
that fits the limit. Write positive warmups through the ordinary checked path;
malformed empty trailers have an empty witness. Compression-negative blocks set
`requireFieldAcceptance = false`, since decompression is required even when fields
are discarded. This exception applies to the faulty block, not its warmups.

HPACK sessions retain both the minimum table limit since the previous block and
the final limit. If a settings change would destroy dynamic state needed for an
assertion, stop with a prerequisite failure rather than rebuilding that state.
Likewise, an initial-empty-table test must stop if initial settings require a
clearing update that could hide a prepopulated decoder. An unmet prerequisite
leaves the behavior unexercised; it does not establish an implementation defect.

## Upload cleanup

`exchangeStream` passes `FixtureCleanup[F]` to its source builder. Wrap effect-owned
cleanup with this boundary so cancellation cannot hide cleanup failures:

```scala
client.exchangeStream(
  request,
  cleanup => Stream.exec(upload.onCancel(cleanup(release))),
)
```

The boundary also composes with `guaranteeCase` and `Resource.makeCase` through
`cleanup(release(value, exit))`. Put intentional recovery inside the boundary,
as in `cleanup(release.handleErrorWith(recover))`; recovery outside it does not
erase a recorded failure. The adapter observes FS2 scope finalizers separately.
An opaque effect can consume its own cancellation errors before the adapter sees
them, which is why effect-owned cleanup must use the supplied boundary. A source
without such cleanup can use `_ => stream`.
