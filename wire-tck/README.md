# HTTP wire conformance TCK

The TCK exercises HTTP/1.1, HTTP/2 and HPACK through loopback sockets. Each
`WireCase` is an individual MUnit test, shared by Ember and the Netty comparison
adapter. The peers construct and inspect wire traffic independently of either
implementation's codecs. The [specification baseline](baseline.md) defines the
scope; passing the catalog establishes only the behavior exercised by its cases.

Ordinary Ember runs skip the cases in
[IgnoreRegistry.scala](../wire-testkit/shared/src/main/scala/org/http4s/wire/IgnoreRegistry.scala).
Each entry references a case and records its affected platforms and observed
failure. Comparison runs execute every case, including those normally ignored.

## Running

Run the testkit and Ember suites on JVM, JS or Native:

```sh
sbt ';wire-testkit/test;wire-tck/test'
sbt ';wire-testkitJS/test;wire-tckJS/test'
sbt ';wire-testkitNative/test;wire-tckNative/test'
```

To select a case, use its exact ID as an MUnit tag:

```sh
sbt 'wire-tck/testOnly org.http4s.wire.EmberHttp1ServerSuite -- --include-tags=h1.server.get'
```

Run the full Ember and Netty comparison with `sbt tckCompare`. This task
collects all platform results before checking that every case has an outcome.
Protocol failures and inconclusive deadlines are reported as data; missing
outcomes and infrastructure errors fail the task. A timeout does not establish
protocol rejection. Reports and wire transcripts are written under
`target/wire-tck-reports/`.

## Authoring

Case definitions, ignores and specification metadata live in the Scala testkit
sources. Requirements in
[SpecInventory](../wire-testkit/shared/src/main/scala/org/http4s/wire/SpecInventory.scala)
reference named cases directly and record coverage gaps and exclusions.
See the [authoring guide](authoring.md) for case structure, protocol assertions
and resource ownership.
