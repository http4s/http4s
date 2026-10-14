# HTTP wire conformance TCK

Generated audit and validation artifacts are not version-controlled and have been removed from this checkout. Artifact names and original revision IDs below are historical references, not available files or revisions in the rewritten branch. Use the ordinary test commands in [Running](README.md#running) and `sbt --client tckCompare` to generate fresh results under `target/wire-tck-reports/`.

The catalog tests clients and servers through real loopback sockets. Each `WireCase`
is an individual MUnit test, shared unchanged by Ember and Netty. Ordinary Ember
runs apply `IgnoreRegistry`; comparison runs execute every case.

The phase 4 validation and audit record (`audit/phase4-accepted-status.md`, historical artifact) reports
the completed Scala/platform matrix, implementation deviations and explicit coverage gaps.

## Running

```
sbt --client 'wire-testkit/test' 'wire-tck/test'
sbt --client 'wire-testkitJS/test' 'wire-tckJS/test'
sbt --client 'wire-testkitNative/test' 'wire-tckNative/test'
sbt --client tckCompare
sbt --client 'wire-tck/testOnly org.http4s.wire.EmberHttp1ServerSuite -- --include-tags=h1.server.get'
sbt --client 'wire-tckJS/testOnly org.http4s.wire.EmberHttp1ServerSuite -- --include-tags=h1.server.get'
sbt --client 'wire-tckNative/testOnly org.http4s.wire.EmberHttp1ServerSuite -- --include-tags=h1.server.get'
sbt --client 'wire-tck-compare/testOnly org.http4s.wire.CompareNettyHttp2ServerSuite'
```

Each case also has its stable ID as an MUnit tag. Use `--include-tags=<exact ID>`
to select one case on JVM, JS, or Native; tag matching is exact.

The test commands and comparison task use the selected Scala version. Prefix a command sequence with
`++2.12.21`, `++2.13.18`, or `++3.3.8` to use the repository's version matrix.
`wire-tck` and `wire-testkit` are aggregated into the ordinary platform test roots;
`wire-tck-compare` is deliberately separate. All three modules are unpublished.

Comparison's individual MUnit tests fail normally. `tckCompare` collects the
results for all platforms before reconciling evidence: protocol failures and
inconclusive deadlines are diagnostic; missing outcomes and infrastructure errors
fail the task. A deadline is never proof of rejection. Outcomes and bounded byte
transcripts live under `target/wire-tck-reports/<scala>/<mode>/<platform>/<suite>`.
Comparison also writes `report.md`, `summary.txt`, and a portable `evidence.zip`
containing the complete outcome records, manifests and transcripts. Transcript
references are relative to their JSON records, including in downloaded CI artifacts.
MUnit failures include the first 4,096 transcript characters and the full artifact
path, keeping runner messages small enough for Native while retaining the complete
bounded transcript in the report.
A filtered run intentionally leaves nonselected cases unexercised; it is not
complete-catalog evidence.

## Scope and authoring

See [baseline](baseline.md), [coverage inventory](coverage.md), [validation](validation.md),
and [known failures](known-failures.md). These distinguish exercised behavior,
known deviations, recommendations, and uncovered requirements. Passing tests are
not a claim of complete protocol conformance.

Case bodies show the preconditions, literal bytes or explicit frames, and assertions.
Use a stable `h1|h2.client|server.<name>` ID, a nearby RFC section URL/comment, and
structured `Rfc` metadata. Register table variants separately. Do not infer a
receiver rejection obligation solely from a sender prohibition. State the permitted
alternatives and the test's observation limits. Only `ProtocolFailure` marks an
assertion failure; unexpected harness failures remain blocking. Keep scripted-peer
errors outside any target rejection `.attempt`. Do not accept arbitrary timeout,
cancellation, or socket close as an unspecified error-scope oracle.

The independent HTTP/1 reader, HTTP/2 frame reader and HPACK inspector do not
use either target's codecs. Valid HPACK stimuli use literal encoding; malformed
stimuli can use raw bytes and length overrides. Headers retain occurrences/order
and bodies retain binary bytes. TCP write partitioning does not guarantee peer
read partitioning. Each case owns isolated target/socket resources.

`ServerScenario` makes application behavior explicit; `ClientDriver` exposes
resource-scoped response consumption and streaming uploads. Ember uses public
builders. Netty uses its native HTTP/1 codec and HTTP/2 frame codec/multiplexer
in process, aligned with the repository dependency version. It is a documented
minimal codec pipeline, not an independent application framework.

## Ignore policy

Only `IgnoreRegistry`, loaded from [ignores.tsv](ignores.tsv), may suppress cases
in ordinary runs. The build and runtime consume the same six-column metadata.
Each exact ID records platforms, observed failure, evidence revision, review status and a local
tracking reference. An ignored case is skipped, never counted as passing. Full
comparison suites bypass ignores. Test defects are repaired, not registered as
Ember noncompliance. Provisional entries retain interpretation or observation
limits until reviewed. Production Ember behavior is outside this change's scope.

### Cleanup inside upload effects

`exchangeStream` takes a source builder receiving `FixtureCleanup[F]`. Construct
cleanup belonging to effects with that boundary so cancellation cannot hide its
errors from the case outcome:

```scala
client.exchangeStream(
  request,
  cleanup => Stream.exec(upload.onCancel(cleanup(release))),
)
```

The same boundary composes with `guaranteeCase` and `Resource.makeCase`:
`cleanup(release(value, exit))` preserves the original exit case. Put intentional
error recovery inside the boundary, as in `cleanup(release.handleErrorWith(recover))`.
Handling an error after the boundary does not erase the recorded fixture failure.
FS2 scope finalizers are also observed by the adapter's source owner.

An already-built opaque effect can consume or runtime-report its own cancellation
errors before returning control to the adapter. No effect-parametric wrapper can
retroactively recover those errors. Fixture authors must therefore construct
effect-owned cleanup with the supplied boundary. A source without such cleanup
can use `_ => stream`. There is no raw-stream overload, making this obligation
visible at every streaming call site.
