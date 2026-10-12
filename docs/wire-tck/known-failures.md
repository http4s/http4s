# Known Ember protocol observations

Generated audit and validation artifacts are not version-controlled and have been removed from this checkout. Artifact names and original revision IDs below are historical references, not available files or revisions in the rewritten branch. Run `sbt --client tckCheck` and `sbt --client tckCompare` to generate fresh results under `target/wire-tck-reports/`.

Exact entries in `IgnoreRegistry.scala` suppress ordinary runs only; comparison
always executes every active catalog case. The A4 review below covers exact
Ember observations, not general implementation conformance. Confirmed SHOULD
mismatches remain recommendation deviations. A timeout establishes neither
rejection nor a protocol deviation, and any unresolved platform keeps its
aggregate entry provisional.

The active registry has 153 IDs: 92 confirmed and 61 provisional.
The earlier A4 refresh added three IDs and removed eleven obsolete ignores.
Five removals and two connection-credit evidence updates use the separate 48-cell completion-credit
comparison described below and the accepted independent State source review.
These selected outcomes do not replace or certify the earlier full comparison.

## native-scala-2-12-evidence

Initial Scala 2.12 evidence revision (N1): `54d96bc0687624a656f2d29e72fb7408ab33815f:ee7c8ec851956e02a2bfc99c374b5accefc1594afe9c5a8c7d68d4937890797b`.
The initial Scala 2.12.21 normal run (`audit/phase3-a4-normal-check-2.12.21-initial.zip`, historical artifact)
retains the failed execution, source freeze, reports and transcripts. Its SHA-256
is `935d3f38d6c20700d83f027772c29aa8848679d04d4009129ef4a4fc19a5a59e`.
All three testkit suites passed: JVM 5084, JS 5125 and Native 5078 tests.
Ordinary JVM and JS each passed 654 tests (599 protocol cases and 55 adapter
tests), with 150 registered cases skipped. Native passed 651 tests (596 protocol
cases and 55 adapter tests), skipped 151 and had two nonpassing protocol cases:
one positive ordering failure and one inconclusive deadline. The test runner
counts both as failures; the reports preserve their distinct classifications.
This failed run is evidence, not a passing matrix result or whole-phase acceptance.

The two new Native-only entries below use N1 reports at
`reports/native/EmberHttp2ClientSuite/<case>.json`, with adjacent `.outcome` and
`.wire.txt` members; execution details are in `execution/execution.json`.
Both cases passed on JVM and JS in this Scala 2.12.21 run and on all three Ember
platforms in the earlier [Scala 2.13.18 full comparison](#final-comparison-evidence).
The observations establish neither failure on every Scala version nor a
Scala-version-specific cause. The existing registry selects exact case/platform
pairs, so these Native entries suppress ordinary runs across Scala versions,
including the previously passing 2.13.18 cells. Comparison still executes them.
The other 151 registry entries and their evidence remain unchanged.

## final-comparison-evidence

Final comparison evidence revision (F1): `54d96bc0687624a656f2d29e72fb7408ab33815f:2375bd13ff35b3ce3e5e174601ca1ad1f554a4fdbbb25f5e3b2c6b25a506df6f`.
The final full comparison (`audit/phase3-a4-registry-completion-comparison-2.13.18.zip`, historical artifact) records Scala 2.13.18
on Ember JS (Node.js v24.20.0), JVM (Java 27), Native (Scala Native 0.5.12),
and Netty JVM. All 749 IDs × four target/platform cells reconcile: 2996 outcomes,
comprising 2420 pass, 367 fail and 209 inconclusive. Each Ember platform records
599 pass, 90 fail and 60 inconclusive; Netty records 623 pass, 97 fail and
29 inconclusive. The execution evidence (`audit/phase3-a4-registry-completion-comparison-execution.zip`, historical artifact)
retains the command, successful exit, source freeze and reconciliation: no missing
cases, stale registry IDs, unregistered Ember nonpasses or harness blockers.
This is comparison evidence, not a declaration of general conformance or
whole-phase acceptance; stability and remaining phase obligations require their
own evidence.

F1 report members use `<platform>/<suite>/<case>.json` with adjacent `.outcome`
and `.wire.txt`, using the suite mapping in [A4 evidence](#a4-evidence).
The previous full-run harness timeouts and selected-run limitations below remain
historical facts and are not retroactively cleared by this run.

Relative to the earlier A4 full comparison, 18 case/platform outcomes change:
16 completion-related deadlines now pass (four JS, six JVM, six Native), the
JVM server connection-credit case now positively fails, and the Native
`h2.client.interaction-eager-zero-window-upload` case passes. Relative to C1's
48 selected cells, only the JVM server connection-credit outcome changes, from
inconclusive to fail. Its all-platform positive evidence resolves that entry to
confirmed. The Native intermittent upload ignore remains: its earlier positive
post-ACK DATA violation is preserved, and one passing run does not erase it.
All other registry classifications, exact platform sets and historical records
are unchanged. The three client connection-credit deadlines remain observation
limits, with no deviation established.

## a4-evidence

Current evidence revision (A4): `54d96bc0687624a656f2d29e72fb7408ab33815f:c53ffae541df499791de7494e94a51b53cfcddf07353079964009d7f84d4060f`.
Scala 2.13.18; Ember public API binding on JS, JVM and Native. The complete
full comparison (`audit/phase3-a4-flow-te-upload-comparison-2.13.18.zip`, historical artifact) contains
749 IDs × four target/platform cells. The
independent adjudication (`audit/phase3-a4-latest-outcome-adjudication.zip`, historical artifact)
contains `REPORT.md`, `registry-intake.json`, and `dispositions.json`, including
source snapshots, exact RFC/role/strength reasoning, target/runtime/settings,
report and transcript hashes, and preceding observations.

Except for the explicitly labeled completion-credit rows, each current case/platform
row below is keyed by its exact `Case` ID, `target=ember`
and `platform` in `dispositions.json`. Its original full-comparison JSON member is
`<platform>/<suite>/<case>.json`; the adjacent `<case>.wire.txt` is the bounded
wire transcript. These keys provide per-cell review evidence without duplicating
the archived report or transcripts in every tracking entry.

| ID prefix | Suite directory |
|---|---|
| `h1.client.` | `CompareEmberHttp1ClientSuite` |
| `h1.server.` | `CompareEmberHttp1ServerSuite` |
| `h2.client.` | `CompareEmberHttp2ClientSuite` |
| `h2.server.` | `CompareEmberHttp2ServerSuite` |

The earlier full comparison has 467 Ember nonpasses comprising 258 mandatory deviations, 12 recommendation
deviations, 193 observation limits and four excluded negative-window cells.
No source/harness defect is classified as production noncompliance. Two harness
timeouts in the full run remain validation blockers even though each selected
replay passed once. This registry refresh is not whole-phase acceptance.

The six initial removals have passing cells on every Ember platform in A4 and in
both the peer-push comparison (`audit/phase3-a4-peer-push-comparison-2.13.18.zip`, historical artifact)
and finite-header comparison (`audit/phase3-a4-finite-header-comparison-2.13.18.zip`, historical artifact).
Their exact prior revisions/member keys are retained in each cell's
`historicalCells`. Corrected case semantics, rather than passing results alone,
resolve the historical observations as described below.

## completion-credit-evidence

Selected evidence revision (C1): `54d96bc0687624a656f2d29e72fb7408ab33815f:66b8934ace465bc7ed58d12391e20428ac1086e1be387baefb4b26c6da7da35e`.
The completion-padding evidence (`audit/phase3-a4-completion-padding-evidence.zip`, historical artifact)
contains `comparison-summary.json`, the source repair and controls, and the exact
48 selected cells: 12 IDs × Ember JS/JVM/Native and Netty JVM. The result is
42 pass, two fail and four inconclusive; Netty passes all 12 and Ember passes ten
on each platform. This is selected-case evidence, not a reconciled whole catalog
or a full comparison rerun. The fresh State review (`audit/phase3-a4-completion-padding-postreview.md`, historical artifact)
independently resolves the main completion correction (A4CP1) and challenge
repair (A4CP1E). Its separate test-fixture sequencing finding A4CP1F does not
change the main cases or these target outcomes; that repair/review remains a
separate acceptance item. This record does not assert whole-phase acceptance.

Report members use `comparison/<platform>/<suite>/<case>.json` with adjacent
`.outcome` and `.wire.txt`, using the suite mapping above. JS/Native valid single-case
captures additionally have `.run.json` with `native_count=1`; the zero-test
comma-tag attempts and their stale captures under `invalid-zero-selection` are
excluded. Their suite manifest/complete files describe only the last selected
execution, not all cases. JVM evidence covers the two selected-role runs.

The correction provisions enough late completion credit for remaining content
plus padding across the unchanged 4096-frame bound: remaining content +
4096 × 256. The tracked window retains its existing credit, and only its deficit
is added; the other flow-control window also receives sufficient completion
credit. These late grants occur after each case's tested boundary. Earlier
negative/zero/one-credit, SETTINGS/ACK/PING and first-prefix checks remain. The
old content-sized completion grants could stall lawful padded DATA, so resolved
old deadlines reflect a case correction, not an Ember production change or a
general conformance result. No ignore retains that fixture defect.

Both original negative-window IDs remain excluded from the earlier A4 review;
both roles had JS pass and JVM/Native inconclusive cells in that full run.
The intervening 8/8 follow-up is historical outcome evidence only. C1 supplies
newer passing outcomes after the independently accepted completion correction.
The client negative-window ID remains unregistered.

| Selected ID | Ember JS | Ember JVM | Ember Native | Netty JVM |
|---|---|---|---|---|
| `h2.client.connection-credit-independent` | inconclusive | inconclusive | inconclusive | pass |
| `h2.client.maximum-stream-window-update` | pass | pass | pass | pass |
| `h2.client.negative-stream-window` | pass | pass | pass | pass |
| `h2.client.stream-credit-boundary` | pass | pass | pass | pass |
| `h2.server.connection-credit-independent` | fail | inconclusive | fail | pass |
| `h2.server.maximum-stream-window-update` | pass | pass | pass | pass |
| `h2.server.negative-stream-window` | pass | pass | pass | pass |
| `h2.server.peer-concurrency-zero-allows-responses` | pass | pass | pass | pass |
| `h2.server.reset-closes-blocked-stream` | pass | pass | pass | pass |
| `h2.server.stream-credit-boundary` | pass | pass | pass | pass |
| `h2.server.window-update-after-peer-end` | pass | pass | pass | pass |
| `h2.server.window-update-on-closed-stream` | pass | pass | pass | pass |

Only the two connection-credit IDs retain nonpasses. Their exact platform
classifications appear in their tracking entries below. No timeout is promoted
to a mandatory deviation.

## historical-phase-2-evidence

The original phase-2 snapshot registered 160 distinct case IDs. Retirement of
`h2.server.connect-authority-form` left 159 before this refresh. The
3,008 phase-2 outcomes and transcripts (`validation/phase2-2.13.18.zip`, historical artifact), revision
`2d3a58784d510279cd56d1d11f5c500a57f05e6e:b4a41bd2cb227e053e8a4539e3c4973fdef39aa9bad8d54ffb54ee55ee62777e`,
are unchanged. Below, 159 original case records remain verbatim and describe
that earlier snapshot. The `h2.server.connect-authority-form` record retains its
original platform outcomes and error text with an explicit retirement annotation.

## h1-client-chunk-extension-bad-whitespace

Case: `h1.client.chunk-extension-bad-whitespace`. Current review: **confirmed**.

RFC review: [RFC 9110 §5.6.3](https://www.rfc-editor.org/rfc/rfc9110.html#section-5.6.3); [RFC 9112 §7.1](https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1); [RFC 9112 §7.1.1](https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | ChunkedEncodingError rejects valid chunk-extension BWS |
| jvm | fail | confirmed mandatory deviation (MUST) | ChunkedEncodingError rejects valid chunk-extension BWS |
| native | fail | confirmed mandatory deviation (MUST) | ChunkedEncodingError rejects valid chunk-extension BWS |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h1.client.chunk-extension-bad-whitespace`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Recipients parse the BWS positions defined by the chunk extension grammar.](https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1.1).

```text
org.http4s.wire.ProtocolFailure: the target rejected a valid response: org.http4s.ember.core.EmberException$ChunkedEncodingError: Failed to parse chunked header : Right(3 	; 	wire-token 	= 	alpha
)
```

</details>

## h1-client-error-response-during-upload

Case: `h1.client.error-response-during-upload`. Current review: **provisional**.

RFC review: [RFC 9112 §9.5](https://www.rfc-editor.org/rfc/rfc9112.html#section-9.5). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (SHOULD; no deviation established) | 8-second timeout after observed 32768-byte prefix and complete 413/Connection: close; stopping/closure unestablished |
| jvm | inconclusive | provisional observation limit (SHOULD; no deviation established) | 8-second timeout after observed 32768-byte prefix and complete 413/Connection: close; stopping/closure unestablished |
| native | inconclusive | provisional observation limit (SHOULD; no deviation established) | 8-second timeout after observed 32768-byte prefix and complete 413/Connection: close; stopping/closure unestablished |

The corrected 32-KiB prefix reaches the peer before the complete error response. This resolves the old coalescing/three-byte-prefix fixture gap for these executions; the subsequent deadline still cannot establish a SHOULD monitoring/stopping deviation.

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h1.client.error-response-during-upload`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: SHOULD. [A client monitors responses during upload and stops sending when an error closes the connection.](https://www.rfc-editor.org/rfc/rfc9112.html#section-9.5).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

This recommendation is not classified as a MUST-level conformance failure.

</details>

## h1-client-generated-asterisk-form

Case: `h1.client.generated-asterisk-form`. Current review: **confirmed**.

RFC review: [RFC 9112 §3.2.4](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2.4). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | public Ember URI/encoder binding sends OPTIONS /* instead of OPTIONS * |
| jvm | fail | confirmed mandatory deviation (MUST) | public Ember URI/encoder binding sends OPTIONS /* instead of OPTIONS * |
| native | fail | confirmed mandatory deviation (MUST) | public Ember URI/encoder binding sends OPTIONS /* instead of OPTIONS * |

The reviewed public Ember URI/encoder binding converts the requested `*` to `/*`. This is a confirmed observation through that binding, not a claim about every possible application API.

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h1.client.generated-asterisk-form`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A server-wide OPTIONS request uses only an asterisk as its request-target.](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2.4).

```text
org.http4s.wire.ProtocolFailure: expected: *
observed: /*
```

Review the target URI representation and adapter fidelity before confirming this observation.

</details>

## h1-client-informational-before-final-response

Case: `h1.client.informational-before-final-response`. Current review: **resolved; active ignore removed**.

Remove the active ignore. The corrected observer accepts an exposed bodyless interim response or a valid final response; it does not require every API to return the final response. A4 and both preceding archives pass on JS/JVM/Native. The separate interim/next-request-association case remains registered.

RFC review: [RFC 9112 §9.2](https://www.rfc-editor.org/rfc/rfc9112.html#section-9.2). Evidence: [A4 and preceding cells](#a4-evidence), keyed by this ID and platform.

| Platform | A4 | Peer-push | Finite-header |
|---|---|---|---|
| js | pass | pass | pass |
| jvm | pass | pass | pass |
| native | pass | pass | pass |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h1.client.informational-before-final-response`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Informational responses belong to the outstanding request until its final response.](https://www.rfc-editor.org/rfc/rfc9112.html#section-9.2).

```text
org.http4s.wire.ProtocolFailure: expected: 200
observed: 100
```

Review must distinguish API exposure of interim responses from failure to parse or associate the final wire response.

</details>

## h1-client-informational-does-not-use-close-delimitation

Case: `h1.client.informational-does-not-use-close-delimitation`. Current review: **resolved; active ignore removed**.

Remove the active ignore. The corrected observer accepts an exposed bodyless interim response or a valid final response; it does not require every API to return the final response. A4 and both preceding archives pass on JS/JVM/Native. The separate interim/next-request-association case remains registered.

RFC review: [RFC 9112 §6.3](https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3). Evidence: [A4 and preceding cells](#a4-evidence), keyed by this ID and platform.

| Platform | A4 | Peer-push | Finite-header |
|---|---|---|---|
| js | pass | pass | pass |
| jvm | pass | pass | pass |
| native | pass | pass | pass |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h1.client.informational-does-not-use-close-delimitation`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [An informational response ends at its header boundary before the final response starts.](https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3).

```text
org.http4s.wire.ProtocolFailure: expected: 200
observed: 103
```

Review must distinguish API exposure of interim responses from failure to parse or associate the final wire response.

</details>

## h1-client-interaction-interim-next-request-association

Case: `h1.client.interaction-interim-next-request-association`. Current review: **confirmed**.

RFC review: [RFC 9112 §9.2](https://www.rfc-editor.org/rfc/rfc9112.html#section-9.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | expected: 201; observed: 103 |
| jvm | fail | confirmed mandatory deviation (MUST) | expected: 201; observed: 103 |
| native | fail | confirmed mandatory deviation (MUST) | expected: 201; observed: 103 |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h1.client.interaction-interim-next-request-association`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Interim and final responses to one request cannot be associated with a later request on the connection.](https://www.rfc-editor.org/rfc/rfc9112.html#section-9.2).

```text
org.http4s.wire.ProtocolFailure: expected: 201
observed: 103
```

</details>

## h1-client-multiple-informational-boundaries

Case: `h1.client.multiple-informational-boundaries`. Current review: **resolved; active ignore removed**.

Remove the active ignore. The corrected observer accepts an exposed bodyless interim response or a valid final response; it does not require every API to return the final response. A4 and both preceding archives pass on JS/JVM/Native. The separate interim/next-request-association case remains registered.

RFC review: [RFC 9112 §6.3](https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3). Evidence: [A4 and preceding cells](#a4-evidence), keyed by this ID and platform.

| Platform | A4 | Peer-push | Finite-header |
|---|---|---|---|
| js | pass | pass | pass |
| jvm | pass | pass | pass |
| native | pass | pass | pass |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h1.client.multiple-informational-boundaries`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Each informational response is bodyless and does not consume the next response.](https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3).

```text
org.http4s.wire.ProtocolFailure: expected: 200
observed: 100
```

Review must distinguish API exposure of interim responses from failure to parse or associate the final wire response.

</details>

## h1-client-obsolete-fold-multiple

Case: `h1.client.obsolete-fold-multiple`. Current review: **confirmed**.

RFC review: [RFC 9112 §5.2](https://www.rfc-editor.org/rfc/rfc9112.html#section-5.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | InvalidHeaderWhitespace rejects response requiring obs-fold acceptance |
| jvm | fail | confirmed mandatory deviation (MUST) | InvalidHeaderWhitespace rejects response requiring obs-fold acceptance |
| native | fail | confirmed mandatory deviation (MUST) | InvalidHeaderWhitespace rejects response requiring obs-fold acceptance |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h1.client.obsolete-fold-multiple`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A user agent must replace each obsolete response field fold with one or more spaces.](https://www.rfc-editor.org/rfc/rfc9112.html#section-5.2).

```text
org.http4s.wire.ProtocolFailure: the target rejected a response requiring acceptance: org.http4s.ember.core.Parser$HeaderP$ParseHeadersError: Encountered Error Attempting to Parse Headers - InvalidHeaderWhitespace
```

</details>

## h1-client-obsolete-fold-space

Case: `h1.client.obsolete-fold-space`. Current review: **confirmed**.

RFC review: [RFC 9112 §5.2](https://www.rfc-editor.org/rfc/rfc9112.html#section-5.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | InvalidHeaderWhitespace rejects response requiring obs-fold acceptance |
| jvm | fail | confirmed mandatory deviation (MUST) | InvalidHeaderWhitespace rejects response requiring obs-fold acceptance |
| native | fail | confirmed mandatory deviation (MUST) | InvalidHeaderWhitespace rejects response requiring obs-fold acceptance |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h1.client.obsolete-fold-space`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A user agent must replace each obsolete response field fold with one or more spaces.](https://www.rfc-editor.org/rfc/rfc9112.html#section-5.2).

```text
org.http4s.wire.ProtocolFailure: the target rejected a response requiring acceptance: org.http4s.ember.core.Parser$HeaderP$ParseHeadersError: Encountered Error Attempting to Parse Headers - InvalidHeaderWhitespace
```

</details>

## h1-client-obsolete-fold-tab

Case: `h1.client.obsolete-fold-tab`. Current review: **confirmed**.

RFC review: [RFC 9112 §5.2](https://www.rfc-editor.org/rfc/rfc9112.html#section-5.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | InvalidHeaderWhitespace rejects response requiring obs-fold acceptance |
| jvm | fail | confirmed mandatory deviation (MUST) | InvalidHeaderWhitespace rejects response requiring obs-fold acceptance |
| native | fail | confirmed mandatory deviation (MUST) | InvalidHeaderWhitespace rejects response requiring obs-fold acceptance |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h1.client.obsolete-fold-tab`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A user agent must replace each obsolete response field fold with one or more spaces.](https://www.rfc-editor.org/rfc/rfc9112.html#section-5.2).

```text
org.http4s.wire.ProtocolFailure: the target rejected a response requiring acceptance: org.http4s.ember.core.Parser$HeaderP$ParseHeadersError: Encountered Error Attempting to Parse Headers - InvalidHeaderWhitespace
```

</details>

## h1-server-absolute-form-empty-host

Case: `h1.server.absolute-form-empty-host`. Current review: **confirmed**.

RFC review: [RFC 9110 §4.2.1](https://www.rfc-editor.org/rfc/rfc9110.html#section-4.2.1); [RFC 9112 §3.2.2](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | an empty http URI host was accepted: Some(200) |
| jvm | fail | confirmed mandatory deviation (MUST) | an empty http URI host was accepted: Some(200) |
| native | fail | confirmed mandatory deviation (MUST) | an empty http URI host was accepted: Some(200) |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h1.server.absolute-form-empty-host`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A recipient must reject an http URI with an empty host identifier.](https://www.rfc-editor.org/rfc/rfc9110.html#section-4.2.1).

```text
org.http4s.wire.ProtocolFailure: an empty http URI host was accepted: Some(200)
```

</details>

## h1-server-chunk-extension-bad-whitespace

Case: `h1.server.chunk-extension-bad-whitespace`. Current review: **confirmed**.

RFC review: [RFC 9110 §5.6.3](https://www.rfc-editor.org/rfc/rfc9110.html#section-5.6.3); [RFC 9112 §7.1](https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1); [RFC 9112 §7.1.1](https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | expected: 200; observed: 500 |
| jvm | fail | confirmed mandatory deviation (MUST) | expected: 200; observed: 500 |
| native | fail | confirmed mandatory deviation (MUST) | expected: 200; observed: 500 |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h1.server.chunk-extension-bad-whitespace`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Recipients parse the BWS positions defined by the chunk extension grammar.](https://www.rfc-editor.org/rfc/rfc9112.html#section-7.1.1).

```text
org.http4s.wire.ProtocolFailure: expected: 200
observed: 500
```

</details>

## h1-server-duplicate-host-case-insensitive

Case: `h1.server.duplicate-host-case-insensitive`. Current review: **confirmed**.

RFC review: [RFC 9110 §4.1](https://www.rfc-editor.org/rfc/rfc9110.html#section-4.1); [RFC 9110 §7.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-7.2); [RFC 9112 §3.2](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | expected: 400; observed: 200 |
| jvm | fail | confirmed mandatory deviation (MUST) | expected: 400; observed: 200 |
| native | fail | confirmed mandatory deviation (MUST) | expected: 400; observed: 200 |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h1.server.duplicate-host-case-insensitive`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Multiple Host field lines or an invalid Host field value require a 400 response.](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2).

```text
org.http4s.wire.ProtocolFailure: expected: 400
observed: 200
```

</details>

## h1-server-duplicate-host-different

Case: `h1.server.duplicate-host-different`. Current review: **confirmed**.

RFC review: [RFC 9110 §4.1](https://www.rfc-editor.org/rfc/rfc9110.html#section-4.1); [RFC 9110 §7.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-7.2); [RFC 9112 §3.2](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | expected: 400; observed: 200 |
| jvm | fail | confirmed mandatory deviation (MUST) | expected: 400; observed: 200 |
| native | fail | confirmed mandatory deviation (MUST) | expected: 400; observed: 200 |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h1.server.duplicate-host-different`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Multiple Host field lines or an invalid Host field value require a 400 response.](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2).

```text
org.http4s.wire.ProtocolFailure: expected: 400
observed: 200
```

</details>

## h1-server-duplicate-host-identical

Case: `h1.server.duplicate-host-identical`. Current review: **confirmed**.

RFC review: [RFC 9110 §4.1](https://www.rfc-editor.org/rfc/rfc9110.html#section-4.1); [RFC 9110 §7.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-7.2); [RFC 9112 §3.2](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | expected: 400; observed: 200 |
| jvm | fail | confirmed mandatory deviation (MUST) | expected: 400; observed: 200 |
| native | fail | confirmed mandatory deviation (MUST) | expected: 400; observed: 200 |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h1.server.duplicate-host-identical`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Multiple Host field lines or an invalid Host field value require a 400 response.](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2).

```text
org.http4s.wire.ProtocolFailure: expected: 400
observed: 200
```

</details>

## h1-server-field-name-delimiter

Case: `h1.server.field-name-delimiter`. Current review: **confirmed**.

RFC review: [RFC 9110 §5.1](https://www.rfc-editor.org/rfc/rfc9110.html#section-5.1); [RFC 9110 §5.6.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-5.6.2); [RFC 9112 §2.2](https://www.rfc-editor.org/rfc/rfc9112.html#section-2.2); [RFC 9112 §5](https://www.rfc-editor.org/rfc/rfc9112.html#section-5). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed recommendation deviation (SHOULD; not MUST) | expected: 400; observed: 200 |
| jvm | fail | confirmed recommendation deviation (SHOULD; not MUST) | expected: 400; observed: 200 |
| native | fail | confirmed recommendation deviation (SHOULD; not MUST) | expected: 400; observed: 200 |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h1.server.field-name-delimiter`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: SHOULD. [Field names are tokens; malformed messages should receive 400 and connection closure.](https://www.rfc-editor.org/rfc/rfc9112.html#section-2.2).

```text
org.http4s.wire.ProtocolFailure: expected: 400
observed: 200
```

This recommendation is not classified as a MUST-level conformance failure.

</details>

## h1-server-head-response-has-no-content

Case: `h1.server.head-response-has-no-content`. Current review: **confirmed**.

RFC review: [RFC 9112 §6.3](https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | additional bytes followed a response requiring connection closure |
| jvm | fail | confirmed mandatory deviation (MUST) | additional bytes followed a response requiring connection closure |
| native | fail | confirmed mandatory deviation (MUST) | additional bytes followed a response requiring connection closure |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h1.server.head-response-has-no-content`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [HEAD, 204, and 304 responses terminate at the end of headers and cannot send content or trailers.](https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3).

```text
org.http4s.wire.ProtocolFailure: expected: ByteVector(empty)
observed: ByteVector(3 bytes, 0x616263)
```

</details>

## h1-server-host-comma

Case: `h1.server.host-comma`. Current review: **confirmed**.

RFC review: [RFC 9110 §4.1](https://www.rfc-editor.org/rfc/rfc9110.html#section-4.1); [RFC 9110 §7.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-7.2); [RFC 9112 §3.2](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | expected: 400; observed: 200 |
| jvm | fail | confirmed mandatory deviation (MUST) | expected: 400; observed: 200 |
| native | fail | confirmed mandatory deviation (MUST) | expected: 400; observed: 200 |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h1.server.host-comma`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Multiple Host field lines or an invalid Host field value require a 400 response.](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2).

```text
org.http4s.wire.ProtocolFailure: expected: 400
observed: 200
```

</details>

## h1-server-host-nondigit-port

Case: `h1.server.host-nondigit-port`. Current review: **confirmed**.

RFC review: [RFC 9110 §4.1](https://www.rfc-editor.org/rfc/rfc9110.html#section-4.1); [RFC 9110 §7.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-7.2); [RFC 9112 §3.2](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | expected: 400; observed: 200 |
| jvm | fail | confirmed mandatory deviation (MUST) | expected: 400; observed: 200 |
| native | fail | confirmed mandatory deviation (MUST) | expected: 400; observed: 200 |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h1.server.host-nondigit-port`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Multiple Host field lines or an invalid Host field value require a 400 response.](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2).

```text
org.http4s.wire.ProtocolFailure: expected: 400
observed: 200
```

</details>

## h1-server-host-path

Case: `h1.server.host-path`. Current review: **confirmed**.

RFC review: [RFC 9110 §4.1](https://www.rfc-editor.org/rfc/rfc9110.html#section-4.1); [RFC 9110 §7.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-7.2); [RFC 9112 §3.2](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | expected: 400; observed: 200 |
| jvm | fail | confirmed mandatory deviation (MUST) | expected: 400; observed: 200 |
| native | fail | confirmed mandatory deviation (MUST) | expected: 400; observed: 200 |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h1.server.host-path`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Multiple Host field lines or an invalid Host field value require a 400 response.](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2).

```text
org.http4s.wire.ProtocolFailure: expected: 400
observed: 200
```

</details>

## h1-server-host-space

Case: `h1.server.host-space`. Current review: **confirmed**.

RFC review: [RFC 9110 §4.1](https://www.rfc-editor.org/rfc/rfc9110.html#section-4.1); [RFC 9110 §7.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-7.2); [RFC 9112 §3.2](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | expected: 400; observed: 200 |
| jvm | fail | confirmed mandatory deviation (MUST) | expected: 400; observed: 200 |
| native | fail | confirmed mandatory deviation (MUST) | expected: 400; observed: 200 |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h1.server.host-space`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Multiple Host field lines or an invalid Host field value require a 400 response.](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2).

```text
org.http4s.wire.ProtocolFailure: expected: 400
observed: 200
```

</details>

## h1-server-host-unclosed-ipv6-literal

Case: `h1.server.host-unclosed-ipv6-literal`. Current review: **confirmed**.

RFC review: [RFC 9110 §4.1](https://www.rfc-editor.org/rfc/rfc9110.html#section-4.1); [RFC 9110 §7.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-7.2); [RFC 9112 §3.2](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | expected: 400; observed: 200 |
| jvm | fail | confirmed mandatory deviation (MUST) | expected: 400; observed: 200 |
| native | fail | confirmed mandatory deviation (MUST) | expected: 400; observed: 200 |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h1.server.host-unclosed-ipv6-literal`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [An invalid Host field value requires a 400 response.](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2).

```text
org.http4s.wire.ProtocolFailure: expected: 400
observed: 200
```

</details>

## h1-server-host-userinfo

Case: `h1.server.host-userinfo`. Current review: **confirmed**.

RFC review: [RFC 9110 §4.1](https://www.rfc-editor.org/rfc/rfc9110.html#section-4.1); [RFC 9110 §7.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-7.2); [RFC 9112 §3.2](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | expected: 400; observed: 200 |
| jvm | fail | confirmed mandatory deviation (MUST) | expected: 400; observed: 200 |
| native | fail | confirmed mandatory deviation (MUST) | expected: 400; observed: 200 |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h1.server.host-userinfo`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Multiple Host field lines or an invalid Host field value require a 400 response.](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2).

```text
org.http4s.wire.ProtocolFailure: expected: 400
observed: 200
```

</details>

## h1-server-http10-close-overrides-keep-alive

Case: `h1.server.http10-close-overrides-keep-alive`. Current review: **provisional**.

RFC review: [RFC 9112 §9.3](https://www.rfc-editor.org/rfc/rfc9112.html#section-9.3); [RFC 9112 §9.6](https://www.rfc-editor.org/rfc/rfc9112.html#section-9.6). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h1.server.http10-close-overrides-keep-alive`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [An HTTP/1.0 close option takes precedence over keep-alive.](https://www.rfc-editor.org/rfc/rfc9112.html#section-9.3).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

</details>

## h1-server-http10-response-has-no-transfer-encoding

Case: `h1.server.http10-response-has-no-transfer-encoding`. Current review: **confirmed**.

RFC review: [RFC 9112 §6.1](https://www.rfc-editor.org/rfc/rfc9112.html#section-6.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | expected: Vector(); observed: Vector(chunked) |
| jvm | fail | confirmed mandatory deviation (MUST) | expected: Vector(); observed: Vector(chunked) |
| native | fail | confirmed mandatory deviation (MUST) | expected: Vector(); observed: Vector(chunked) |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h1.server.http10-response-has-no-transfer-encoding`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A server must not emit Transfer-Encoding when the corresponding request indicates HTTP/1.0.](https://www.rfc-editor.org/rfc/rfc9112.html#section-6.1).

```text
org.http4s.wire.ProtocolFailure: expected: Vector()
observed: Vector(chunked)
```

</details>

## h1-server-leading-empty-line

Case: `h1.server.leading-empty-line`. Current review: **confirmed**.

RFC review: [RFC 9112 §2.2](https://www.rfc-editor.org/rfc/rfc9112.html#section-2.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed recommendation deviation (SHOULD; not MUST) | expected: 200; observed: 400 |
| jvm | fail | confirmed recommendation deviation (SHOULD; not MUST) | expected: 200; observed: 400 |
| native | fail | confirmed recommendation deviation (SHOULD; not MUST) | expected: 200; observed: 400 |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h1.server.leading-empty-line`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: SHOULD. [A server expecting a request-line should ignore at least one preceding empty line.](https://www.rfc-editor.org/rfc/rfc9112.html#section-2.2).

```text
org.http4s.wire.ProtocolFailure: expected: 200
observed: 400
```

This recommendation is not classified as a MUST-level conformance failure.

</details>

## h1-server-missing-host

Case: `h1.server.missing-host`. Current review: **confirmed**.

RFC review: [RFC 9110 §4.1](https://www.rfc-editor.org/rfc/rfc9110.html#section-4.1); [RFC 9110 §7.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-7.2); [RFC 9112 §3.2](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | expected: 400; observed: 200 |
| jvm | fail | confirmed mandatory deviation (MUST) | expected: 400; observed: 200 |
| native | fail | confirmed mandatory deviation (MUST) | expected: 400; observed: 200 |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h1.server.missing-host`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Reject an HTTP/1.1 request with no Host field using 400.](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2).

```text
org.http4s.wire.ProtocolFailure: expected: 400
observed: 200
```

</details>

## h1-server-no-content-response-has-no-content

Case: `h1.server.no-content-response-has-no-content`. Current review: **confirmed**.

RFC review: [RFC 9112 §6.3](https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | additional bytes followed a response requiring connection closure |
| jvm | fail | confirmed mandatory deviation (MUST) | additional bytes followed a response requiring connection closure |
| native | fail | confirmed mandatory deviation (MUST) | additional bytes followed a response requiring connection closure |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h1.server.no-content-response-has-no-content`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [HEAD, 204, and 304 responses terminate at the end of headers and cannot send content or trailers.](https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3).

```text
org.http4s.wire.ProtocolFailure: expected: ByteVector(empty)
observed: ByteVector(3 bytes, 0x616263)
```

</details>

## h1-server-not-modified-response-has-no-content

Case: `h1.server.not-modified-response-has-no-content`. Current review: **confirmed**.

RFC review: [RFC 9112 §6.3](https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | additional bytes followed a response requiring connection closure |
| jvm | fail | confirmed mandatory deviation (MUST) | additional bytes followed a response requiring connection closure |
| native | fail | confirmed mandatory deviation (MUST) | additional bytes followed a response requiring connection closure |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h1.server.not-modified-response-has-no-content`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [HEAD, 204, and 304 responses terminate at the end of headers and cannot send content or trailers.](https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3).

```text
org.http4s.wire.ProtocolFailure: expected: ByteVector(empty)
observed: ByteVector(3 bytes, 0x616263)
```

</details>

## h1-server-unsupported-transfer-coding

Case: `h1.server.unsupported-transfer-coding`. Current review: **confirmed**.

RFC review: [RFC 9112 §6.1](https://www.rfc-editor.org/rfc/rfc9112.html#section-6.1); [RFC 9112 §6.3](https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed recommendation deviation (SHOULD; not MUST) | expected unsupported coding or length-required policy, observed 400 |
| jvm | fail | confirmed recommendation deviation (SHOULD; not MUST) | expected unsupported coding or length-required policy, observed 400 |
| native | fail | confirmed recommendation deviation (SHOULD; not MUST) | expected unsupported coding or length-required policy, observed 400 |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h1.server.unsupported-transfer-coding`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: SHOULD. [An unknown request transfer coding should produce 501; a length-required policy may instead produce 411.](https://www.rfc-editor.org/rfc/rfc9112.html#section-6.1).

```text
org.http4s.wire.ProtocolFailure: expected unsupported coding or length-required policy, observed 400
```

This recommendation is not classified as a MUST-level conformance failure.

</details>

## h2-client-connection-credit-independent

Case: `h2.client.connection-credit-independent`. Current review: **provisional**; C1 evidence with independently accepted main-source correction.

RFC review: [RFC 9113 §6.9.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.2); [RFC 9113 §6.9.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.1). Evidence: [C1 selected comparison](#completion-credit-evidence), keyed by this ID and platform.

| Platform | Selected outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required control/progress witness incomplete |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required control/progress witness incomplete |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required control/progress witness incomplete |

C1 changes late completion credit only. All three platforms time out without a complete success or positive over-credit witness; no mandatory target defect follows from this deadline.

<details>
<summary>Earlier A4 review, before completion-credit correction</summary>

Case: `h2.client.connection-credit-independent`. Current review: **provisional**.

RFC review: [RFC 9113 §5.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.2.1); [RFC 9113 §6.9.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.1); [RFC 9113 §6.9.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout after revised PING coordination; successful transfer/over-credit witness unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout after revised PING coordination; successful transfer/over-credit witness unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout after revised PING coordination; successful transfer/over-credit witness unestablished |

The corrected fixture sends PING without demanding exact window exhaustion. JS/JVM capture three 16-KiB DATA frames without the next PING ACK; Native reaches the stream-update/SETTINGS stage. Finite absence of the next witness proves no mandatory target deviation.

</details>

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.connection-credit-independent`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [SETTINGS and stream WINDOW_UPDATE cannot replenish exhausted upload connection credit.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.2).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

</details>

## h2-client-goaway-debug-data

Case: `h2.client.goaway-debug-data`. Current review: **resolved; active ignore removed**.

Remove the active JVM/Native ignore. The historical failure reports generic PROTOCOL_ERROR (code 1); the reviewed oracle permits generic codes 1/2 under RFC 9113 §5.4 and allows GOAWAY to complete or interrupt the in-flight request. A4 and both preceding archives pass on all three platforms. The earlier Native intermittent record remains historical, not a currently demonstrated protocol defect.

RFC review: [RFC 9113 §6.8](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.8). Evidence: [A4 and preceding cells](#a4-evidence), keyed by this ID and platform.

| Platform | A4 | Peer-push | Finite-header |
|---|---|---|---|
| js | pass | pass | pass |
| jvm | pass | pass | pass |
| native | pass | pass | pass |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.goaway-debug-data`. Review: provisional.

Platforms/outcomes: jvm fail; native intermittent failure in an earlier unchanged case. The latest Native execution passed.

Requirement strength: MUST. [GOAWAY may complete or interrupt an existing stream; debug bytes and unknown codes do not create a protocol error.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.8).

```text
org.http4s.wire.ProtocolFailure: valid GOAWAY elicited a protocol error: Frame(7,0,0,ByteVector(8 bytes, 0x0000000000000001))
```

Earlier Native evidence is retained in the intermittent observation record (`validation/phase2-intermittent-goaway.jsonl`, historical artifact), revision `2d3a58784d510279cd56d1d11f5c500a57f05e6e:bcea7bea27ec20cfb43d156450540d95992fb9ef6cd1c6e3450c0a199ad97118`. Retained provisionally until the independent review resolves this timing-dependent observation.

</details>

## h2-client-goaway-unknown-error

Case: `h2.client.goaway-unknown-error`. Current review: **resolved; active ignore removed**.

Remove the active JVM/Native ignore. The historical failure reports generic PROTOCOL_ERROR (code 1); the reviewed oracle permits generic codes 1/2 under RFC 9113 §5.4 and allows GOAWAY to complete or interrupt the in-flight request. A4 and both preceding archives pass on all three platforms. The earlier Native intermittent record remains historical, not a currently demonstrated protocol defect.

RFC review: [RFC 9113 §7](https://www.rfc-editor.org/rfc/rfc9113.html#section-7). Evidence: [A4 and preceding cells](#a4-evidence), keyed by this ID and platform.

| Platform | A4 | Peer-push | Finite-header |
|---|---|---|---|
| js | pass | pass | pass |
| jvm | pass | pass | pass |
| native | pass | pass | pass |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.goaway-unknown-error`. Review: provisional.

Platforms/outcomes: jvm fail; native intermittent failure in an earlier unchanged case. The latest Native execution passed.

Requirement strength: MUST. [GOAWAY may complete or interrupt an existing stream; debug bytes and unknown codes do not create a protocol error.](https://www.rfc-editor.org/rfc/rfc9113.html#section-7).

```text
org.http4s.wire.ProtocolFailure: valid GOAWAY elicited a protocol error: Frame(7,0,0,ByteVector(8 bytes, 0x0000000000000001))
```

Earlier Native evidence is retained in the intermittent observation record (`validation/phase2-intermittent-goaway.jsonl`, historical artifact), revision `2d3a58784d510279cd56d1d11f5c500a57f05e6e:bcea7bea27ec20cfb43d156450540d95992fb9ef6cd1c6e3450c0a199ad97118`. Retained provisionally until the independent review resolves this timing-dependent observation.

</details>

## h2-client-hpack-encoder-minimum-before-restore

Case: `h2.client.hpack-encoder-minimum-before-restore`. Current review: **confirmed**.

RFC review: [RFC 7541 §4.2](https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2); [RFC 9113 §6.5.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | invalid HPACK index after acknowledged encoder table reduction |
| jvm | fail | confirmed mandatory deviation (MUST) | invalid HPACK index after acknowledged encoder table reduction |
| native | fail | confirmed mandatory deviation (MUST) | invalid HPACK index after acknowledged encoder table reduction |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.hpack-encoder-minimum-before-restore`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [If the peer limit falls to zero and rises before the next block, the encoder signals the minimum before any restored capacity.](https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2).

```text
org.http4s.wire.ProtocolFailure: target emitted invalid HPACK or framing: java.lang.IllegalArgumentException: invalid HPACK index
```

</details>

## h2-client-hpack-encoder-reduction-zero

Case: `h2.client.hpack-encoder-reduction-zero`. Current review: **confirmed**.

RFC review: [RFC 7541 §4.2](https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2); [RFC 9113 §6.5.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | invalid HPACK index after acknowledged encoder table reduction |
| jvm | fail | confirmed mandatory deviation (MUST) | invalid HPACK index after acknowledged encoder table reduction |
| native | fail | confirmed mandatory deviation (MUST) | invalid HPACK index after acknowledged encoder table reduction |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.hpack-encoder-reduction-zero`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [An acknowledged reduction to zero is signaled at the beginning of the next block and no dynamic entries remain usable.](https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2).

```text
org.http4s.wire.ProtocolFailure: target emitted invalid HPACK or framing: java.lang.IllegalArgumentException: invalid HPACK index
```

</details>

## h2-client-hpack-reject-huffman-padding-eight

Case: `h2.client.hpack-reject-huffman-padding-eight`. Current review: **confirmed**.

RFC review: [RFC 7541 §https://www.rfc-editor.org/rfc/rfc7541.html#appendix-B](https://www.rfc-editor.org/rfc/rfc7541.html#appendix-B); [RFC 7541 §5.2](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2); [RFC 9113 §4.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-4.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |
| jvm | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |
| native | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.hpack-reject-huffman-padding-eight`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Huffman padding longer than seven bits is a decoding error.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2).

```text
org.http4s.wire.ProtocolFailure: target client consumed malformed HPACK
```

</details>

## h2-client-hpack-reject-huffman-string-truncated

Case: `h2.client.hpack-reject-huffman-string-truncated`. Current review: **confirmed**.

RFC review: [RFC 7541 §5.1](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1); [RFC 7541 §5.2](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2); [RFC 9113 §4.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-4.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |
| jvm | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |
| native | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.hpack-reject-huffman-string-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A Huffman length cannot advertise more encoded octets than the field block contains.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2).

```text
org.http4s.wire.ProtocolFailure: target client consumed malformed HPACK
```

</details>

## h2-client-hpack-reject-incremental-name-integer-truncated

Case: `h2.client.hpack-reject-incremental-name-integer-truncated`. Current review: **confirmed**.

RFC review: [RFC 7541 §5.1](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1); [RFC 7541 §5.2](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2); [RFC 9113 §4.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-4.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |
| jvm | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |
| native | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.hpack-reject-incremental-name-integer-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A saturated six-bit incremental name index requires its terminating continuation octet.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1).

```text
org.http4s.wire.ProtocolFailure: target client consumed malformed HPACK
```

</details>

## h2-client-hpack-reject-index-integer-truncated

Case: `h2.client.hpack-reject-index-integer-truncated`. Current review: **confirmed**.

RFC review: [RFC 7541 §5.1](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1); [RFC 7541 §5.2](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2); [RFC 9113 §4.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-4.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |
| jvm | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |
| native | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.hpack-reject-index-integer-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A saturated indexed prefix requires its terminating continuation octet.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1).

```text
org.http4s.wire.ProtocolFailure: target client consumed malformed HPACK
```

</details>

## h2-client-hpack-reject-name-length-truncated

Case: `h2.client.hpack-reject-name-length-truncated`. Current review: **confirmed**.

RFC review: [RFC 7541 §5.1](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1); [RFC 7541 §5.2](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2); [RFC 9113 §4.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-4.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |
| jvm | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |
| native | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.hpack-reject-name-length-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A saturated literal name length prefix requires its terminating continuation octet.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1).

```text
org.http4s.wire.ProtocolFailure: target client consumed malformed HPACK
```

</details>

## h2-client-hpack-reject-name-truncated

Case: `h2.client.hpack-reject-name-truncated`. Current review: **confirmed**.

RFC review: [RFC 7541 §5.1](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1); [RFC 7541 §5.2](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2); [RFC 9113 §4.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-4.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |
| jvm | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |
| native | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.hpack-reject-name-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A literal name cannot extend beyond the completed field block.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2).

```text
org.http4s.wire.ProtocolFailure: target client consumed malformed HPACK
```

</details>

## h2-client-hpack-reject-never-name-integer-truncated

Case: `h2.client.hpack-reject-never-name-integer-truncated`. Current review: **confirmed**.

RFC review: [RFC 7541 §5.1](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1); [RFC 7541 §5.2](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2); [RFC 9113 §4.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-4.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |
| jvm | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |
| native | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.hpack-reject-never-name-integer-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A saturated four-bit never-indexed name index requires its terminating continuation octet.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1).

```text
org.http4s.wire.ProtocolFailure: target client consumed malformed HPACK
```

</details>

## h2-client-hpack-reject-string-length-truncated

Case: `h2.client.hpack-reject-string-length-truncated`. Current review: **confirmed**.

RFC review: [RFC 7541 §5.1](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1); [RFC 7541 §5.2](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2); [RFC 9113 §4.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-4.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |
| jvm | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |
| native | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.hpack-reject-string-length-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A saturated string length prefix requires its terminating continuation octet.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1).

```text
org.http4s.wire.ProtocolFailure: target client consumed malformed HPACK
```

</details>

## h2-client-hpack-reject-update-after-index

Case: `h2.client.hpack-reject-update-after-index`. Current review: **confirmed**.

RFC review: [RFC 7541 §4.2](https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2); [RFC 9113 §4.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-4.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |
| jvm | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |
| native | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.hpack-reject-update-after-index`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A dynamic table size update after an indexed field is a decoding error.](https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2).

```text
org.http4s.wire.ProtocolFailure: target client consumed malformed HPACK
```

</details>

## h2-client-hpack-reject-update-after-literal

Case: `h2.client.hpack-reject-update-after-literal`. Current review: **confirmed**.

RFC review: [RFC 7541 §4.2](https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2); [RFC 9113 §4.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-4.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |
| jvm | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |
| native | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.hpack-reject-update-after-literal`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A dynamic table size update after a literal field is a decoding error.](https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2).

```text
org.http4s.wire.ProtocolFailure: target client consumed malformed HPACK
```

</details>

## h2-client-hpack-reject-value-missing

Case: `h2.client.hpack-reject-value-missing`. Current review: **confirmed**.

RFC review: [RFC 7541 §5.1](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1); [RFC 7541 §5.2](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2); [RFC 7541 §6.2.2](https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.2); [RFC 9113 §4.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-4.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |
| jvm | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |
| native | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.hpack-reject-value-missing`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A literal name must be followed by a complete value string representation.](https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.2).

```text
org.http4s.wire.ProtocolFailure: target client consumed malformed HPACK
```

</details>

## h2-client-hpack-reject-value-truncated

Case: `h2.client.hpack-reject-value-truncated`. Current review: **confirmed**.

RFC review: [RFC 7541 §5.1](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1); [RFC 7541 §5.2](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2); [RFC 9113 §4.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-4.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |
| jvm | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |
| native | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.hpack-reject-value-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A literal value cannot extend beyond the completed field block.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2).

```text
org.http4s.wire.ProtocolFailure: target client consumed malformed HPACK
```

</details>

## h2-client-hpack-reject-without-name-integer-truncated

Case: `h2.client.hpack-reject-without-name-integer-truncated`. Current review: **confirmed**.

RFC review: [RFC 7541 §5.1](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1); [RFC 7541 §5.2](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2); [RFC 9113 §4.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-4.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |
| jvm | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |
| native | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.hpack-reject-without-name-integer-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A saturated four-bit non-indexing name index requires its terminating continuation octet.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1).

```text
org.http4s.wire.ProtocolFailure: target client consumed malformed HPACK
```

</details>

## h2-client-message-empty-trailers

Case: `h2.client.message-empty-trailers`. Current review: **confirmed**.

RFC review: [RFC 7541 §3.1](https://www.rfc-editor.org/rfc/rfc7541.html#section-3.1); [RFC 9113 §8.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (syntax/MUST) | COMPRESSION_ERROR rejects valid empty trailing HEADERS |
| jvm | fail | confirmed mandatory deviation (syntax/MUST) | COMPRESSION_ERROR rejects valid empty trailing HEADERS |
| native | fail | confirmed mandatory deviation (syntax/MUST) | COMPRESSION_ERROR rejects valid empty trailing HEADERS |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.message-empty-trailers`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [An empty final trailer section can terminate a message after DATA.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1).

```text
org.http4s.wire.ProtocolFailure: the target rejected valid HTTP/2 input: java.util.concurrent.CancellationException: Received GoAway, cancelling: GoAway(identifier=0, lastStreamId=0, errorCode=CompressionError, additionalDebugData=None)
```

</details>

## h2-client-reject-connection-field

Case: `h2.client.reject-connection-field`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.2.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-connection-field`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Connection-specific header fields must not occur in HTTP/2 messages.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-field-name-colon

Case: `h2.client.reject-field-name-colon`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-field-name-colon`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-field-name-control-boundary

Case: `h2.client.reject-field-name-control-boundary`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-field-name-control-boundary`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-field-name-del

Case: `h2.client.reject-field-name-del`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-field-name-del`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-field-name-nul

Case: `h2.client.reject-field-name-nul`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-field-name-nul`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-field-name-obs-text-high

Case: `h2.client.reject-field-name-obs-text-high`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-field-name-obs-text-high`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-field-name-obs-text-low

Case: `h2.client.reject-field-name-obs-text-low`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-field-name-obs-text-low`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-field-name-space

Case: `h2.client.reject-field-name-space`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-field-name-space`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-field-name-tab

Case: `h2.client.reject-field-name-tab`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-field-name-tab`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-field-name-uppercase-boundary

Case: `h2.client.reject-field-name-uppercase-boundary`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-field-name-uppercase-boundary`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-field-value-cr

Case: `h2.client.reject-field-value-cr`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-field-value-cr`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A prohibited response field makes the message malformed and must not be accepted.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-field-value-leading-space

Case: `h2.client.reject-field-value-leading-space`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-field-value-leading-space`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Field values must not begin or end with SP or HTAB.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-field-value-leading-tab

Case: `h2.client.reject-field-value-leading-tab`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-field-value-leading-tab`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Field values must not begin or end with SP or HTAB.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-field-value-lf

Case: `h2.client.reject-field-value-lf`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-field-value-lf`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A prohibited response field makes the message malformed and must not be accepted.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-field-value-nul

Case: `h2.client.reject-field-value-nul`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-field-value-nul`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A prohibited response field makes the message malformed and must not be accepted.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-field-value-trailing-space

Case: `h2.client.reject-field-value-trailing-space`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-field-value-trailing-space`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Field values must not begin or end with SP or HTAB.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-field-value-trailing-tab

Case: `h2.client.reject-field-value-trailing-tab`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-field-value-trailing-tab`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Field values must not begin or end with SP or HTAB.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-headers-padding-equals-payload

Case: `h2.client.reject-headers-padding-equals-payload`. Current review: **confirmed**.

RFC review: [RFC 9113 §4.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-4.2); [RFC 9113 §5.4](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4); [RFC 9113 §6.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | COMPRESSION_ERROR for invalid padding requiring PROTOCOL_ERROR (generic INTERNAL_ERROR also allowed) |
| jvm | fail | confirmed mandatory deviation (MUST) | COMPRESSION_ERROR for invalid padding requiring PROTOCOL_ERROR (generic INTERNAL_ERROR also allowed) |
| native | fail | confirmed mandatory deviation (MUST) | COMPRESSION_ERROR for invalid padding requiring PROTOCOL_ERROR (generic INTERNAL_ERROR also allowed) |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-headers-padding-equals-payload`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Invalid field-block framing requires a connection error; a stream reset alone does not satisfy this requirement.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2).

```text
org.http4s.wire.ProtocolFailure: incorrect GOAWAY error code
```

</details>

## h2-client-reject-headers-padding-exceeds-payload

Case: `h2.client.reject-headers-padding-exceeds-payload`. Current review: **confirmed**.

RFC review: [RFC 9113 §4.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-4.2); [RFC 9113 §5.4](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4); [RFC 9113 §6.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | COMPRESSION_ERROR for invalid padding requiring PROTOCOL_ERROR (generic INTERNAL_ERROR also allowed) |
| jvm | fail | confirmed mandatory deviation (MUST) | COMPRESSION_ERROR for invalid padding requiring PROTOCOL_ERROR (generic INTERNAL_ERROR also allowed) |
| native | fail | confirmed mandatory deviation (MUST) | COMPRESSION_ERROR for invalid padding requiring PROTOCOL_ERROR (generic INTERNAL_ERROR also allowed) |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-headers-padding-exceeds-payload`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Invalid field-block framing requires a connection error; a stream reset alone does not satisfy this requirement.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2).

```text
org.http4s.wire.ProtocolFailure: incorrect GOAWAY error code
```

</details>

## h2-client-reject-headers-stream-zero

Case: `h2.client.reject-headers-stream-zero`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §6.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-headers-stream-zero`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [Invalid field-block framing requires a connection error; a stream reset alone does not satisfy this requirement.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

</details>

## h2-client-reject-informational-end-stream

Case: `h2.client.reject-informational-end-stream`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1); [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-informational-end-stream`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [An informational HEADERS block with END_STREAM is malformed.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-keep-alive-field

Case: `h2.client.reject-keep-alive-field`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.2.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-keep-alive-field`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Connection-specific header fields must not occur in HTTP/2 messages.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-preface-first-ping

Case: `h2.client.reject-preface-first-ping`. Current review: **provisional**.

RFC review: [RFC 9113 §3.4](https://www.rfc-editor.org/rfc/rfc9113.html#section-3.4); [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-preface-first-ping`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [A server preface that starts with PING is a connection error.](https://www.rfc-editor.org/rfc/rfc9113.html#section-3.4).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

</details>

## h2-client-reject-priority-stream-zero

Case: `h2.client.reject-priority-stream-zero`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §6.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-priority-stream-zero`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A PRIORITY frame on stream zero is a connection error.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.3).

```text
org.http4s.wire.ProtocolFailure: target completed an exchange despite a connection protocol error
```

</details>

## h2-client-reject-proxy-connection-field

Case: `h2.client.reject-proxy-connection-field`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.2.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-proxy-connection-field`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Connection-specific header fields must not occur in HTTP/2 messages.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-pseudo-header-after-regular-field

Case: `h2.client.reject-pseudo-header-after-regular-field`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-pseudo-header-after-regular-field`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Every pseudo-header field must precede every regular field line.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-push-invalid-field

Case: `h2.client.reject-push-invalid-field`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.4.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-push-invalid-field`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [A promise must contain a valid request field section; acknowledged push disablement instead requires a connection error.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.4.1).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

</details>

## h2-client-reject-push-missing-method

Case: `h2.client.reject-push-missing-method`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.4.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-push-missing-method`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [A promise must contain a complete request field section; acknowledged push disablement instead requires a connection error.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.4.1).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

</details>

## h2-client-reject-push-request-content

Case: `h2.client.reject-push-request-content`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.4](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.4). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-push-request-content`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A promise indicating request content must be rejected; acknowledged push disablement instead requires a connection error.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.4).

```text
org.http4s.wire.ProtocolFailure: incorrect RST_STREAM error code
```

</details>

## h2-client-reject-push-stream-zero

Case: `h2.client.reject-push-stream-zero`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §6.6](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.6). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-push-stream-zero`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [PUSH_PROMISE must be associated with a nonzero stream.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.6).

```text
org.http4s.wire.ProtocolFailure: incorrect RST_STREAM error code
```

</details>

## h2-client-reject-push-unauthoritative-origin

Case: `h2.client.reject-push-unauthoritative-origin`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.4](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.4). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-push-unauthoritative-origin`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A promise for an origin for which the peer is not authoritative must be rejected; acknowledged push disablement instead requires a connection error.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.4).

```text
org.http4s.wire.ProtocolFailure: incorrect RST_STREAM error code
```

</details>

## h2-client-reject-push-unsafe-method

Case: `h2.client.reject-push-unsafe-method`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.4](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.4). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-push-unsafe-method`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [An unsafe promised request requires rejection of the promised stream; acknowledged push disablement instead requires a connection error.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.4).

```text
org.http4s.wire.ProtocolFailure: incorrect RST_STREAM error code
```

</details>

## h2-client-reject-request-authority-pseudo-header

Case: `h2.client.reject-request-authority-pseudo-header`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-request-authority-pseudo-header`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Request-only pseudo-header fields must not occur in responses.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-request-method-pseudo-header

Case: `h2.client.reject-request-method-pseudo-header`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-request-method-pseudo-header`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Request-only pseudo-header fields must not occur in responses.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-request-path-pseudo-header

Case: `h2.client.reject-request-path-pseudo-header`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-request-path-pseudo-header`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Request-only pseudo-header fields must not occur in responses.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-request-scheme-pseudo-header

Case: `h2.client.reject-request-scheme-pseudo-header`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-request-scheme-pseudo-header`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Request-only pseudo-header fields must not occur in responses.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-response-te

Case: `h2.client.reject-response-te`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.2.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-response-te`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The TE exception is for requests; a response must not contain this connection-specific field.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-settings-server-enable-push-one

Case: `h2.client.reject-settings-server-enable-push-one`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §6.5.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-settings-server-enable-push-one`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Clients reject a server explicitly enabling push with SETTINGS_ENABLE_PUSH equal to one.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2).

```text
org.http4s.wire.ProtocolFailure: target completed an exchange despite a connection protocol error
```

</details>

## h2-client-reject-switching-protocols

Case: `h2.client.reject-switching-protocols`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.6](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.6). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST NOT) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST NOT) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST NOT) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-switching-protocols`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [HTTP/2 does not support status 101 Switching Protocols.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.6).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-trailer-prohibited-field

Case: `h2.client.reject-trailer-prohibited-field`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1); [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1); [RFC 9113 §8.2.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-trailer-prohibited-field`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A connection-specific field in a trailer section makes the message malformed.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-trailer-pseudo-header

Case: `h2.client.reject-trailer-pseudo-header`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1); [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1); [RFC 9113 §8.2.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2); [RFC 9113 §8.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-trailer-pseudo-header`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Pseudo-header fields are prohibited in trailer sections.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-trailer-uppercase-name

Case: `h2.client.reject-trailer-uppercase-name`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1); [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1); [RFC 9113 §8.2.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-trailer-uppercase-name`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Mandatory field validation applies to trailers as well as initial header sections.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-trailers-without-end-stream

Case: `h2.client.reject-trailers-without-end-stream`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1); [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1); [RFC 9113 §8.2.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST/MUST NOT) | the client consumed a deliberately malformed response |

Native now has the previously missing cell: trailing HEADERS without END_STREAM followed by terminal DATA still completes body/trailers successfully. All three platforms have positive mandatory acceptance deviations; the partial SETTINGS ACK at the Native trace tail is not rejection.

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-trailers-without-end-stream`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A second field section after request headers or a final response must carry END_STREAM.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-transfer-encoding-field

Case: `h2.client.reject-transfer-encoding-field`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.2.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-transfer-encoding-field`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Connection-specific header fields must not occur in HTTP/2 messages.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-undefined-pseudo-header

Case: `h2.client.reject-undefined-pseudo-header`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-undefined-pseudo-header`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Unnegotiated undefined pseudo-header fields make the message malformed.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-upgrade-field

Case: `h2.client.reject-upgrade-field`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.2.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-upgrade-field`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Connection-specific header fields must not occur in HTTP/2 messages.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-reject-uppercase-field-name

Case: `h2.client.reject-uppercase-field-name`. Current review: **confirmed**.

RFC review: [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed mandatory deviation (MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.reject-uppercase-field-name`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A prohibited response field makes the message malformed and must not be accepted.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

</details>

## h2-client-settings-stream-window-overflow

Case: `h2.client.settings-stream-window-overflow`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §6.9.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.settings-stream-window-overflow`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [An initial-window SETTINGS change overflowing an upload window is a connection error.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.2).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

</details>

## h2-client-settings-window-maximum

Case: `h2.client.settings-window-maximum`. Current review: **confirmed**.

RFC review: [RFC 9113 §6.5.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2); [RFC 9113 §6.9.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | FLOW_CONTROL_ERROR rejects legal INITIAL_WINDOW_SIZE=2147483647 |
| jvm | fail | confirmed mandatory deviation (MUST) | FLOW_CONTROL_ERROR rejects legal INITIAL_WINDOW_SIZE=2147483647 |
| native | fail | confirmed mandatory deviation (MUST) | FLOW_CONTROL_ERROR rejects legal INITIAL_WINDOW_SIZE=2147483647 |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.settings-window-maximum`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The defined setting accepts this unsigned boundary value and is acknowledged.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2).

```text
org.http4s.wire.ProtocolFailure: target rejected a valid control-frame exchange: java.util.concurrent.CancellationException: Received GoAway, cancelling: GoAway(identifier=0, lastStreamId=0, errorCode=FlowControlError, additionalDebugData=None)
```

```text
org.http4s.wire.ProtocolFailure: unexpected GOAWAY before the control barrier: Frame(7,0,0,ByteVector(8 bytes, 0x0000000000000003))
```

</details>

## h2-client-unpromised-server-headers

Case: `h2.client.unpromised-server-headers`. Current review: **provisional**.

RFC review: [RFC 9113 §5.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.1); [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.unpromised-server-headers`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [HEADERS cannot open an unpromised server-initiated stream.](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.1).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

</details>

## h2-client-validate-field-empty-name

Case: `h2.client.validate-field-empty-name`. Current review: **confirmed**.

RFC review: [RFC 9113 §5.4](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4); [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (conditional MUST) | COMPRESSION_ERROR for detected HTTP field-name error in structurally valid HPACK |
| jvm | fail | confirmed mandatory deviation (conditional MUST) | COMPRESSION_ERROR for detected HTTP field-name error in structurally valid HPACK |
| native | fail | confirmed mandatory deviation (conditional MUST) | COMPRESSION_ERROR for detected HTTP field-name error in structurally valid HPACK |

Full token validation is SHOULD, but once the empty name is detected the HTTP message error requires PROTOCOL_ERROR; generic INTERNAL_ERROR/escalation is allowed. The observed COMPRESSION_ERROR is incorrect because HPACK is structurally valid. Confirmation concerns this conditional MUST, not promotion of token validation to MUST.

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.validate-field-empty-name`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: SHOULD. [Full HTTP field-name token validation is recommended beyond the mandatory minimum character restrictions.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: incorrect GOAWAY error code
```

This recommendation is not classified as a MUST-level conformance failure.

</details>

## h2-client-validate-field-separator-name

Case: `h2.client.validate-field-separator-name`. Current review: **confirmed**.

RFC review: [RFC 9110 §5.1](https://www.rfc-editor.org/rfc/rfc9110.html#section-5.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed recommendation deviation (SHOULD; not MUST) | the client consumed a deliberately malformed response |
| jvm | fail | confirmed recommendation deviation (SHOULD; not MUST) | the client consumed a deliberately malformed response |
| native | fail | confirmed recommendation deviation (SHOULD; not MUST) | the client consumed a deliberately malformed response |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.client.validate-field-separator-name`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: SHOULD. [Full HTTP field-name token validation is recommended beyond the mandatory minimum character restrictions.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the client consumed a deliberately malformed response
```

This recommendation is not classified as a MUST-level conformance failure.

</details>

## h2-server-connect-authority-form

Case: `h2.server.connect-authority-form`. Review: retired (A4CR1 oracle correction).

Historical phase-2 platforms/outcomes: js fail, jvm fail, native fail.

Historical asserted requirement strength: MUST. [A CONNECT request uses :method and host:port :authority, omitting :scheme and :path.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.5).

```text
org.http4s.wire.ProtocolFailure: expected: 1
observed: 3
```

Disposition: removed from executable discovery and the active ignore registry.
The case required a successful 200 response and application publication from an
origin binding, but [CONNECT support is optional](https://www.rfc-editor.org/rfc/rfc9110.html#section-9.1);
[CONNECT semantics](https://www.rfc-editor.org/rfc/rfc9110.html#section-9.3.6) and
[HTTP/2 CONNECT](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.5) do not impose
that success obligation on this fixture. A supporting proxy sends 2xx only after
establishing the target connection. The independent actual-case probe rejected a
valid CONNECT answered with 501, while 200 with method-only application publication
passed despite no authority observation. `RequestData` has no dedicated CONNECT
authority observation; allowing 501 or a generic reset would not prove parsing.

The archived Ember resets demonstrate an acceptance limitation, not the invented
mandatory-success predicate. Retirement corrects the oracle and records an
acceptance/authority-observation gap; it is neither a passing test nor an Ember
fix. The four malformed-CONNECT cases remain active and unchanged. Probe sources,
input hashes and transcripts are retained in the
CONNECT review evidence (`audit/phase3-a4-connect-review-probes.zip`, historical artifact).

## h2-server-connection-credit-independent

Case: `h2.server.connection-credit-independent`. Current review: **confirmed**; F1 full-comparison evidence after the independently accepted main-source correction.

RFC review: [RFC 9113 §6.9.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.2); [RFC 9113 §6.9.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.1). Evidence: [F1 final comparison](#final-comparison-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST NOT) | DATA payload 16384 exceeds remaining connection credit 16383 before completion grants |
| jvm | fail | confirmed mandatory deviation (MUST NOT) | DATA payload 16384 exceeds remaining connection credit 16383 before completion grants |
| native | fail | confirmed mandatory deviation (MUST NOT) | DATA payload 16384 exceeds remaining connection credit 16383 before completion grants |

On each platform the server sends a fourth 16384-byte DATA payload after three
such payloads consume 49152 of the initial 65535 connection octets. No connection
WINDOW_UPDATE precedes this overspend. The 64-KiB transcript cap omits the final
209 payload bytes; the visible frame header and actual case's full-payload read
and size assertion jointly establish the violation. This is positive evidence,
not a timeout inference. The earlier JVM deadlines remain recorded below.

<details>
<summary>Earlier C1 selected comparison</summary>

Case: `h2.server.connection-credit-independent`. Current review: **provisional**; C1 evidence with independently accepted main-source correction.

RFC review: [RFC 9113 §6.9.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.2); [RFC 9113 §6.9.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.1). Evidence: [C1 selected comparison](#completion-credit-evidence), keyed by this ID and platform.

| Platform | Selected outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST NOT) | DATA payload 16384 exceeds remaining connection credit 16383 before completion grants |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required control/progress witness incomplete |
| native | fail | confirmed mandatory deviation (MUST NOT) | DATA payload 16384 exceeds remaining connection credit 16383 before completion grants |

C1 changes late completion credit only. The JS/Native positive overspend occurs before those grants; JVM timeout remains inconclusive. The bounded wire capture omits part of the final DATA payload, (209 omitted bytes), so the visible header and actual-case payload assertion support the finding. Aggregate status stays provisional.

</details>

<details>
<summary>Earlier A4 review, before completion-credit correction</summary>

Case: `h2.server.connection-credit-independent`. Current review: **provisional**.

RFC review: [RFC 9113 §6.9.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.1); [RFC 9113 §6.9.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST NOT) | DATA payload 16384 exceeds remaining credit 16383 |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | fail | confirmed mandatory deviation (MUST NOT) | DATA payload 16384 exceeds remaining credit 16383 |

JS/Native positively declare a fourth 16-KiB DATA payload with only 16,383 connection-credit bytes remaining. The trace cap omits the final 203 bytes; payload completeness uses the actual-case assertion with the visible header. JVM stops after 49,152 bytes and times out. Earlier JVM overspend remains historical evidence; it is not substituted for this run. Aggregate status remains provisional.

</details>

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.connection-credit-independent`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Only connection WINDOW_UPDATE replenishes the initial 65535 connection octets.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.2).

```text
org.http4s.wire.ProtocolFailure: DATA payload 16384 exceeds remaining credit 16383
```

</details>

## h2-server-hpack-encoder-minimum-before-restore

Case: `h2.server.hpack-encoder-minimum-before-restore`. Current review: **confirmed**.

RFC review: [RFC 7541 §4.2](https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2); [RFC 9113 §6.5.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | invalid HPACK index after acknowledged encoder table reduction |
| jvm | fail | confirmed mandatory deviation (MUST) | invalid HPACK index after acknowledged encoder table reduction |
| native | fail | confirmed mandatory deviation (MUST) | invalid HPACK index after acknowledged encoder table reduction |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.hpack-encoder-minimum-before-restore`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [If the peer limit falls to zero and rises before the next block, the encoder signals the minimum before any restored capacity.](https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2).

```text
org.http4s.wire.ProtocolFailure: target emitted invalid HPACK or framing: java.lang.IllegalArgumentException: invalid HPACK index
```

</details>

## h2-server-hpack-encoder-reduction-zero

Case: `h2.server.hpack-encoder-reduction-zero`. Current review: **confirmed**.

RFC review: [RFC 7541 §4.2](https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2); [RFC 9113 §6.5.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | invalid HPACK index after acknowledged encoder table reduction |
| jvm | fail | confirmed mandatory deviation (MUST) | invalid HPACK index after acknowledged encoder table reduction |
| native | fail | confirmed mandatory deviation (MUST) | invalid HPACK index after acknowledged encoder table reduction |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.hpack-encoder-reduction-zero`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [An acknowledged reduction to zero is signaled at the beginning of the next block and no dynamic entries remain usable.](https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2).

```text
org.http4s.wire.ProtocolFailure: target emitted invalid HPACK or framing: java.lang.IllegalArgumentException: invalid HPACK index
```

</details>

## h2-server-hpack-reject-huffman-padding-eight

Case: `h2.server.hpack-reject-huffman-padding-eight`. Current review: **provisional**.

RFC review: [RFC 7541 §5.2](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2); [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.hpack-reject-huffman-padding-eight`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Huffman padding longer than seven bits is a decoding error.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2).

```text
org.http4s.wire.ProtocolFailure: malformed HPACK reached the application
```

</details>

## h2-server-hpack-reject-huffman-string-truncated

Case: `h2.server.hpack-reject-huffman-string-truncated`. Current review: **provisional**.

RFC review: [RFC 7541 §5.2](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2); [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.hpack-reject-huffman-string-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A Huffman length cannot advertise more encoded octets than the field block contains.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2).

```text
org.http4s.wire.ProtocolFailure: malformed HPACK reached the application
```

</details>

## h2-server-hpack-reject-incremental-name-integer-truncated

Case: `h2.server.hpack-reject-incremental-name-integer-truncated`. Current review: **provisional**.

RFC review: [RFC 7541 §5.1](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1); [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.hpack-reject-incremental-name-integer-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A saturated six-bit incremental name index requires its terminating continuation octet.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1).

```text
org.http4s.wire.ProtocolFailure: malformed HPACK reached the application
```

</details>

## h2-server-hpack-reject-index-integer-truncated

Case: `h2.server.hpack-reject-index-integer-truncated`. Current review: **provisional**.

RFC review: [RFC 7541 §5.1](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1); [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.hpack-reject-index-integer-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A saturated indexed prefix requires its terminating continuation octet.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1).

```text
org.http4s.wire.ProtocolFailure: malformed HPACK reached the application
```

</details>

## h2-server-hpack-reject-name-length-truncated

Case: `h2.server.hpack-reject-name-length-truncated`. Current review: **provisional**.

RFC review: [RFC 7541 §5.1](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1); [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.hpack-reject-name-length-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A saturated literal name length prefix requires its terminating continuation octet.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1).

```text
org.http4s.wire.ProtocolFailure: malformed HPACK reached the application
```

</details>

## h2-server-hpack-reject-name-truncated

Case: `h2.server.hpack-reject-name-truncated`. Current review: **provisional**.

RFC review: [RFC 7541 §5.2](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2); [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.hpack-reject-name-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A literal name cannot extend beyond the completed field block.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2).

```text
org.http4s.wire.ProtocolFailure: malformed HPACK reached the application
```

</details>

## h2-server-hpack-reject-never-name-integer-truncated

Case: `h2.server.hpack-reject-never-name-integer-truncated`. Current review: **provisional**.

RFC review: [RFC 7541 §5.1](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1); [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.hpack-reject-never-name-integer-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A saturated four-bit never-indexed name index requires its terminating continuation octet.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1).

```text
org.http4s.wire.ProtocolFailure: malformed HPACK reached the application
```

</details>

## h2-server-hpack-reject-string-length-truncated

Case: `h2.server.hpack-reject-string-length-truncated`. Current review: **provisional**.

RFC review: [RFC 7541 §5.1](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1); [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.hpack-reject-string-length-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A saturated string length prefix requires its terminating continuation octet.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1).

```text
org.http4s.wire.ProtocolFailure: malformed HPACK reached the application
```

</details>

## h2-server-hpack-reject-update-after-index

Case: `h2.server.hpack-reject-update-after-index`. Current review: **provisional**.

RFC review: [RFC 7541 §4.2](https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2); [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.hpack-reject-update-after-index`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A dynamic table size update after an indexed field is a decoding error.](https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2).

```text
org.http4s.wire.ProtocolFailure: malformed HPACK reached the application
```

</details>

## h2-server-hpack-reject-update-after-literal

Case: `h2.server.hpack-reject-update-after-literal`. Current review: **provisional**.

RFC review: [RFC 7541 §4.2](https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2); [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.hpack-reject-update-after-literal`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A dynamic table size update after a literal field is a decoding error.](https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2).

```text
org.http4s.wire.ProtocolFailure: malformed HPACK reached the application
```

</details>

## h2-server-hpack-reject-value-missing

Case: `h2.server.hpack-reject-value-missing`. Current review: **provisional**.

RFC review: [RFC 7541 §6.2.2](https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.2); [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.hpack-reject-value-missing`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A literal name must be followed by a complete value string representation.](https://www.rfc-editor.org/rfc/rfc7541.html#section-6.2.2).

```text
org.http4s.wire.ProtocolFailure: malformed HPACK reached the application
```

</details>

## h2-server-hpack-reject-value-truncated

Case: `h2.server.hpack-reject-value-truncated`. Current review: **provisional**.

RFC review: [RFC 7541 §5.2](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2); [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.hpack-reject-value-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A literal value cannot extend beyond the completed field block.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2).

```text
org.http4s.wire.ProtocolFailure: malformed HPACK reached the application
```

</details>

## h2-server-hpack-reject-without-name-integer-truncated

Case: `h2.server.hpack-reject-without-name-integer-truncated`. Current review: **provisional**.

RFC review: [RFC 7541 §5.1](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1); [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.hpack-reject-without-name-integer-truncated`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A saturated four-bit non-indexing name index requires its terminating continuation octet.](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.1).

```text
org.http4s.wire.ProtocolFailure: malformed HPACK reached the application
```

</details>

## h2-server-maximum-stream-window-update

Case: `h2.server.maximum-stream-window-update`. Current disposition: **resolved; active ignore removed**.

RFC review: [RFC 9113 §6.9](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9). Evidence: [C1 selected comparison](#completion-credit-evidence), keyed by this ID and platform.

| Platform | Selected outcome | Disposition |
|---|---|---|
| js | pass | no active ignore |
| jvm | pass | no active ignore |
| native | pass | no active ignore |

The corrected late completion grants accommodate full DATA payload accounting,
including padding, in both windows without weakening the earlier tested boundary.
The independent main-source review and these selected passes resolve the earlier
provisional registry observation; they do not claim an Ember production fix.

<details>
<summary>Earlier A4 review, before completion-credit correction</summary>

Case: `h2.server.maximum-stream-window-update`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §6.9](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

</details>

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.maximum-stream-window-update`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [The maximum WINDOW_UPDATE increment is valid when the existing window is zero.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

</details>

## h2-server-message-empty-trailers

Case: `h2.server.message-empty-trailers`. Current review: **confirmed**.

RFC review: [RFC 7541 §3.1](https://www.rfc-editor.org/rfc/rfc7541.html#section-3.1); [RFC 9113 §8.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (syntax/MUST) | COMPRESSION_ERROR rejects valid empty trailing HEADERS |
| jvm | fail | confirmed mandatory deviation (syntax/MUST) | COMPRESSION_ERROR rejects valid empty trailing HEADERS |
| native | fail | confirmed mandatory deviation (syntax/MUST) | COMPRESSION_ERROR rejects valid empty trailing HEADERS |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.message-empty-trailers`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [An empty final trailer section can terminate a message after DATA.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1).

```text
org.http4s.wire.ProtocolFailure: expected: 1
observed: 7
```

</details>

## h2-server-negative-stream-window

Case: `h2.server.negative-stream-window`. Current disposition: **resolved; active ignore removed**.

RFC review: [RFC 9113 §6.9.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.2). Evidence: [C1 selected comparison](#completion-credit-evidence), keyed by this ID and platform.

| Platform | Selected outcome | Disposition |
|---|---|---|
| js | pass | no active ignore |
| jvm | pass | no active ignore |
| native | pass | no active ignore |

The corrected late completion grants accommodate full DATA payload accounting,
including padding, in both windows without weakening the earlier tested boundary.
The independent main-source review and these selected passes resolve the earlier
provisional registry observation; they do not claim an Ember production fix.

<details>
<summary>Earlier A4 review, before completion-credit correction</summary>

Case: `h2.server.negative-stream-window`. Current review: **pending separate State oracle review**.

Both negative-window IDs are excluded from A4 certification. The old server
ignore remains unchanged on JS/JVM/Native pending final reconciliation; the client
case is not newly registered. The separate 8/8 selected follow-up archive is
outcome evidence only and does not resolve the completion-padding source defect.

</details>

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.negative-stream-window`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [A SETTINGS reduction preserves a negative stream window until credit becomes positive.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.2).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

</details>

## h2-server-reject-authority-userinfo

Case: `h2.server.reject-authority-userinfo`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.3.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-authority-userinfo`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A request pseudo-header value must obey its referenced method, URI, or path grammar.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector(),ByteVector(empty)))
```

</details>

## h2-server-reject-field-name-colon

Case: `h2.server.reject-field-name-colon`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-field-name-colon`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((x:bad,ok)),ByteVector(empty)))
```

</details>

## h2-server-reject-field-name-control-boundary

Case: `h2.server.reject-field-name-control-boundary`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-field-name-control-boundary`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((xbad,ok)),ByteVector(empty)))
```

</details>

## h2-server-reject-field-name-del

Case: `h2.server.reject-field-name-del`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-field-name-del`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((xbad,ok)),ByteVector(empty)))
```

</details>

## h2-server-reject-field-name-nul

Case: `h2.server.reject-field-name-nul`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-field-name-nul`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((x bad,ok)),ByteVector(empty)))
```

</details>

## h2-server-reject-field-name-obs-text-high

Case: `h2.server.reject-field-name-obs-text-high`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-field-name-obs-text-high`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((xÿbad,ok)),ByteVector(empty)))
```

</details>

## h2-server-reject-field-name-obs-text-low

Case: `h2.server.reject-field-name-obs-text-low`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-field-name-obs-text-low`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((xbad,ok)),ByteVector(empty)))
```

</details>

## h2-server-reject-field-name-space

Case: `h2.server.reject-field-name-space`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-field-name-space`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((x bad,ok)),ByteVector(empty)))
```

</details>

## h2-server-reject-field-name-tab

Case: `h2.server.reject-field-name-tab`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-field-name-tab`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The mandatory field-name character restrictions apply after HPACK decompression.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((x	bad,ok)),ByteVector(empty)))
```

</details>

## h2-server-reject-field-value-cr

Case: `h2.server.reject-field-value-cr`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-field-value-cr`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A prohibited request field makes the message malformed and requires a stream error.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((x-value,a
b)),ByteVector(empty)))
```

</details>

## h2-server-reject-field-value-leading-space

Case: `h2.server.reject-field-value-leading-space`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-field-value-leading-space`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Field values must not begin or end with SP or HTAB.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((x-value, value)),ByteVector(empty)))
```

</details>

## h2-server-reject-field-value-leading-tab

Case: `h2.server.reject-field-value-leading-tab`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-field-value-leading-tab`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Field values must not begin or end with SP or HTAB.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((x-value,	value)),ByteVector(empty)))
```

</details>

## h2-server-reject-field-value-lf

Case: `h2.server.reject-field-value-lf`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-field-value-lf`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A prohibited request field makes the message malformed and requires a stream error.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((x-value,a
b)),ByteVector(empty)))
```

</details>

## h2-server-reject-field-value-nul

Case: `h2.server.reject-field-value-nul`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-field-value-nul`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A prohibited request field makes the message malformed and requires a stream error.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((x-value,a b)),ByteVector(empty)))
```

</details>

## h2-server-reject-field-value-trailing-space

Case: `h2.server.reject-field-value-trailing-space`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-field-value-trailing-space`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Field values must not begin or end with SP or HTAB.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((x-value,value )),ByteVector(empty)))
```

</details>

## h2-server-reject-field-value-trailing-tab

Case: `h2.server.reject-field-value-trailing-tab`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-field-value-trailing-tab`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Field values must not begin or end with SP or HTAB.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((x-value,value	)),ByteVector(empty)))
```

</details>

## h2-server-reject-headers-padding-equals-payload

Case: `h2.server.reject-headers-padding-equals-payload`. Current review: **confirmed**.

RFC review: [RFC 9113 §4.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-4.2); [RFC 9113 §5.4](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4); [RFC 9113 §6.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | COMPRESSION_ERROR for invalid padding requiring PROTOCOL_ERROR (generic INTERNAL_ERROR also allowed) |
| jvm | fail | confirmed mandatory deviation (MUST) | COMPRESSION_ERROR for invalid padding requiring PROTOCOL_ERROR (generic INTERNAL_ERROR also allowed) |
| native | fail | confirmed mandatory deviation (MUST) | COMPRESSION_ERROR for invalid padding requiring PROTOCOL_ERROR (generic INTERNAL_ERROR also allowed) |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-headers-padding-equals-payload`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Invalid field-block framing requires a connection error; a stream reset alone does not satisfy this requirement.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2).

```text
org.http4s.wire.ProtocolFailure: incorrect GOAWAY error code
```

</details>

## h2-server-reject-headers-padding-exceeds-payload

Case: `h2.server.reject-headers-padding-exceeds-payload`. Current review: **confirmed**.

RFC review: [RFC 9113 §4.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-4.2); [RFC 9113 §5.4](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4); [RFC 9113 §6.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | COMPRESSION_ERROR for invalid padding requiring PROTOCOL_ERROR (generic INTERNAL_ERROR also allowed) |
| jvm | fail | confirmed mandatory deviation (MUST) | COMPRESSION_ERROR for invalid padding requiring PROTOCOL_ERROR (generic INTERNAL_ERROR also allowed) |
| native | fail | confirmed mandatory deviation (MUST) | COMPRESSION_ERROR for invalid padding requiring PROTOCOL_ERROR (generic INTERNAL_ERROR also allowed) |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-headers-padding-exceeds-payload`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Invalid field-block framing requires a connection error; a stream reset alone does not satisfy this requirement.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.2).

```text
org.http4s.wire.ProtocolFailure: incorrect GOAWAY error code
```

</details>

## h2-server-reject-keep-alive-field

Case: `h2.server.reject-keep-alive-field`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.2.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-keep-alive-field`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Connection-specific header fields must not occur in HTTP/2 messages.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((keep-alive,test)),ByteVector(empty)))
```

</details>

## h2-server-reject-path-fragment

Case: `h2.server.reject-path-fragment`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.3.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-path-fragment`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A request pseudo-header value must obey its referenced method, URI, or path grammar.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/path#fragment,Vector(),ByteVector(empty)))
```

</details>

## h2-server-reject-preface-first-ping

Case: `h2.server.reject-preface-first-ping`. Current review: **provisional**.

RFC review: [RFC 9113 §3.4](https://www.rfc-editor.org/rfc/rfc9113.html#section-3.4); [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-preface-first-ping`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [The client magic must be immediately followed by a SETTINGS frame.](https://www.rfc-editor.org/rfc/rfc9113.html#section-3.4).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

</details>

## h2-server-reject-priority-stream-zero

Case: `h2.server.reject-priority-stream-zero`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §6.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-priority-stream-zero`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [A PRIORITY frame on stream zero is a connection error.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.3).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

</details>

## h2-server-reject-proxy-connection-field

Case: `h2.server.reject-proxy-connection-field`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.2.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-proxy-connection-field`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Connection-specific header fields must not occur in HTTP/2 messages.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((proxy-connection,test)),ByteVector(empty)))
```

</details>

## h2-server-reject-relative-path

Case: `h2.server.reject-relative-path`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.3.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-relative-path`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A request pseudo-header value must obey its referenced method, URI, or path grammar.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,relative,Vector(),ByteVector(empty)))
```

</details>

## h2-server-reject-repeated-authority

Case: `h2.server.reject-repeated-authority`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-repeated-authority`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The same pseudo-header field name must not occur more than once, even with identical values.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector(),ByteVector(empty)))
```

</details>

## h2-server-reject-trailer-prohibited-field

Case: `h2.server.reject-trailer-prohibited-field`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.2.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-trailer-prohibited-field`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A connection-specific field in a trailer section makes the message malformed.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector(),ByteVector(5 bytes, 0x00017f80ff)))
```

</details>

## h2-server-reject-trailer-pseudo-header

Case: `h2.server.reject-trailer-pseudo-header`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-trailer-pseudo-header`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Pseudo-header fields are prohibited in trailer sections.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector(),ByteVector(5 bytes, 0x00017f80ff)))
```

</details>

## h2-server-reject-trailer-uppercase-name

Case: `h2.server.reject-trailer-uppercase-name`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-trailer-uppercase-name`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Mandatory field validation applies to trailers as well as initial header sections.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector(),ByteVector(5 bytes, 0x00017f80ff)))
```

</details>

## h2-server-reject-trailers-without-end-stream

Case: `h2.server.reject-trailers-without-end-stream`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-trailers-without-end-stream`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [A second field section after request headers or a final response must carry END_STREAM.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector(),ByteVector(5 bytes, 0x00017f80ff)))
```

</details>

## h2-server-reject-transfer-encoding-field

Case: `h2.server.reject-transfer-encoding-field`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.2.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-transfer-encoding-field`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Connection-specific header fields must not occur in HTTP/2 messages.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((transfer-encoding,chunked)),ByteVector(empty)))
```

</details>

## h2-server-reject-upgrade-field

Case: `h2.server.reject-upgrade-field`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.2.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reject-upgrade-field`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Connection-specific header fields must not occur in HTTP/2 messages.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.2).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((upgrade,test)),ByteVector(empty)))
```

</details>

## h2-server-request-split-cookie

Case: `h2.server.request-split-cookie`. Current review: **resolved; active ignore removed**.

Remove the active ignore. The corrected oracle establishes acceptance of a valid split-Cookie request, response completion and request method/path. The ordered-field adapter does not establish a generic non-HTTP/2 application boundary, so RFC 9113 §8.2.3 joining at that boundary remains an explicit inventory gap. A4 and both preceding archives pass on JS/JVM/Native.

RFC review: [RFC 9113 §8.2.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.3). Evidence: [A4 and preceding cells](#a4-evidence), keyed by this ID and platform.

| Platform | A4 | Peer-push | Finite-header |
|---|---|---|---|
| js | pass | pass | pass |
| jvm | pass | pass | pass |
| native | pass | pass | pass |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.request-split-cookie`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [Split Cookie field lines must be joined with semicolon and space before delivery to a generic application.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.3).

```text
org.http4s.wire.ProtocolFailure: expected: Vector(a=b; c=d; e=f)
observed: Vector(a=b, c=d, e=f)
```

</details>

## h2-server-reset-closes-blocked-stream

Case: `h2.server.reset-closes-blocked-stream`. Current disposition: **resolved; active ignore removed**.

RFC review: [RFC 9113 §6.4](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.4). Evidence: [C1 selected comparison](#completion-credit-evidence), keyed by this ID and platform.

| Platform | Selected outcome | Disposition |
|---|---|---|
| js | pass | no active ignore |
| jvm | pass | no active ignore |
| native | pass | no active ignore |

The corrected late completion grants accommodate full DATA payload accounting,
including padding, in both windows without weakening the earlier tested boundary.
The independent main-source review and these selected passes resolve the earlier
provisional registry observation; they do not claim an Ember production fix.

<details>
<summary>Earlier A4 review, before completion-credit correction</summary>

Case: `h2.server.reset-closes-blocked-stream`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §6.4](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.4). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

</details>

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.reset-closes-blocked-stream`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [After processing RST_STREAM a server cannot resume DATA on that stream.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.4).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

</details>

## h2-server-settings-stream-window-overflow

Case: `h2.server.settings-stream-window-overflow`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §6.9.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.settings-stream-window-overflow`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [An initial-window SETTINGS change overflowing an active window is a connection error.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.2).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

</details>

## h2-server-settings-window-maximum

Case: `h2.server.settings-window-maximum`. Current review: **confirmed**.

RFC review: [RFC 9113 §6.5.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2); [RFC 9113 §6.9.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.2). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | FLOW_CONTROL_ERROR rejects legal INITIAL_WINDOW_SIZE=2147483647 |
| jvm | fail | confirmed mandatory deviation (MUST) | FLOW_CONTROL_ERROR rejects legal INITIAL_WINDOW_SIZE=2147483647 |
| native | fail | confirmed mandatory deviation (MUST) | FLOW_CONTROL_ERROR rejects legal INITIAL_WINDOW_SIZE=2147483647 |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.settings-window-maximum`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: MUST. [The defined setting accepts this unsigned boundary value and is acknowledged.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.2).

```text
org.http4s.wire.ProtocolFailure: unexpected GOAWAY before the control barrier: Frame(7,0,0,ByteVector(8 bytes, 0x0000000000000003))
```

</details>

## h2-server-stream-credit-boundary

Case: `h2.server.stream-credit-boundary`. Current disposition: **resolved; active ignore removed**.

RFC review: [RFC 9113 §6.9.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.1). Evidence: [C1 selected comparison](#completion-credit-evidence), keyed by this ID and platform.

| Platform | Selected outcome | Disposition |
|---|---|---|
| js | pass | no active ignore |
| jvm | pass | no active ignore |
| native | pass | no active ignore |

The corrected late completion grants accommodate full DATA payload accounting,
including padding, in both windows without weakening the earlier tested boundary.
The independent main-source review and these selected passes resolve the earlier
provisional registry observation; they do not claim an Ember production fix.

<details>
<summary>Earlier A4 review, before completion-credit correction</summary>

Case: `h2.server.stream-credit-boundary`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §6.9.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

</details>

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.stream-credit-boundary`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [Each DATA payload fits the remaining stream credit and WINDOW_UPDATE restores credit.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.1).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

</details>

## h2-server-validate-field-empty-name

Case: `h2.server.validate-field-empty-name`. Current review: **confirmed**.

RFC review: [RFC 9113 §5.4](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4); [RFC 9113 §8.1.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.1.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (conditional MUST) | COMPRESSION_ERROR for detected HTTP field-name error in structurally valid HPACK |
| jvm | fail | confirmed mandatory deviation (conditional MUST) | COMPRESSION_ERROR for detected HTTP field-name error in structurally valid HPACK |
| native | fail | confirmed mandatory deviation (conditional MUST) | COMPRESSION_ERROR for detected HTTP field-name error in structurally valid HPACK |

Full token validation is SHOULD, but once the empty name is detected the HTTP message error requires PROTOCOL_ERROR; generic INTERNAL_ERROR/escalation is allowed. The observed COMPRESSION_ERROR is incorrect because HPACK is structurally valid. Confirmation concerns this conditional MUST, not promotion of token validation to MUST.

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.validate-field-empty-name`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: SHOULD. [Full HTTP field-name token validation is recommended beyond the mandatory minimum character restrictions.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: incorrect GOAWAY error code
```

This recommendation is not classified as a MUST-level conformance failure.

</details>

## h2-server-validate-field-separator-name

Case: `h2.server.validate-field-separator-name`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §8.2.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (SHOULD; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (SHOULD; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (SHOULD; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.validate-field-separator-name`. Review: provisional.

Platforms/outcomes: js fail, jvm fail, native fail.

Requirement strength: SHOULD. [Full HTTP field-name token validation is recommended beyond the mandatory minimum character restrictions.](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2.1).

```text
org.http4s.wire.ProtocolFailure: the application consumed the malformed request: Some(RequestData(GET,/mapping,Vector((x/bad,value)),ByteVector(empty)))
```

This recommendation is not classified as a MUST-level conformance failure.

</details>

## h2-server-window-update-after-peer-end

Case: `h2.server.window-update-after-peer-end`. Current disposition: **resolved; active ignore removed**.

RFC review: [RFC 9113 §6.9](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9). Evidence: [C1 selected comparison](#completion-credit-evidence), keyed by this ID and platform.

| Platform | Selected outcome | Disposition |
|---|---|---|
| js | pass | no active ignore |
| jvm | pass | no active ignore |
| native | pass | no active ignore |

The corrected late completion grants accommodate full DATA payload accounting,
including padding, in both windows without weakening the earlier tested boundary.
The independent main-source review and these selected passes resolve the earlier
provisional registry observation; they do not claim an Ember production fix.

<details>
<summary>Earlier A4 review, before completion-credit correction</summary>

Case: `h2.server.window-update-after-peer-end`. Current review: **provisional**.

RFC review: [RFC 9113 §5.4.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-5.4.1); [RFC 9113 §6.9](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

</details>

<details>
<summary>Historical phase-2 record (unchanged)</summary>

Case: `h2.server.window-update-after-peer-end`. Review: provisional.

Platforms/outcomes: js inconclusive, jvm inconclusive, native inconclusive.

Requirement strength: MUST. [WINDOW_UPDATE remains valid after the peer sends END_STREAM.](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9).

```text
java.util.concurrent.TimeoutException: 8 seconds
```

The finite deadline is an observation limit; it proves neither rejection nor a protocol deviation.

</details>

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
## h2-client-hpack-reject-huffman-name-padding-eight

Case: `h2.client.hpack-reject-huffman-name-padding-eight`. Current review: **confirmed**.

RFC review: [RFC 7541 §5.2](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2); [RFC 7541 §B](https://www.rfc-editor.org/rfc/rfc7541.html#section-B); [RFC 9113 §4.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-4.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |
| jvm | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |
| native | fail | confirmed mandatory deviation (MUST) | target client consumed malformed HPACK |

The corrected Huffman name `f8 ff` decodes a nonempty token with eight padding bits. Client body consumption positively establishes malformed-block acceptance on all three platforms. The server timeout verdict does not alter this client evidence.

## h2-client-interaction-eager-zero-window-upload

Case: `h2.client.interaction-eager-zero-window-upload`. Current review: **confirmed**.

RFC review: [RFC 9113 §6.5.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.5.3); [RFC 9113 §6.9.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.1); [RFC 9113 §6.9.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.2); [RFC 9113 §6.9.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| native | fail | confirmed mandatory deviation (MUST/MUST NOT) | DATA consumed credit while the stream window was zero |

Native emits SETTINGS ACK and request HEADERS before the fixture releases its six-byte body, then sends six DATA bytes while the stream window remains zero, before any WINDOW_UPDATE. This excludes pre-ACK in-flight data. JS/JVM and both prior Native cells pass; retain this positively witnessed intermittent deviation only on Native.

JS/JVM: A4 pass, no active ignore.

## h2-server-hpack-reject-huffman-name-padding-eight

Case: `h2.server.hpack-reject-huffman-name-padding-eight`. Current review: **provisional**.

RFC review: [RFC 7541 §5.2](https://www.rfc-editor.org/rfc/rfc7541.html#section-5.2); [RFC 7541 §B](https://www.rfc-editor.org/rfc/rfc7541.html#section-B); [RFC 9113 §4.3](https://www.rfc-editor.org/rfc/rfc9113.html#section-4.3). Evidence: [A4](#a4-evidence), keyed by this ID and platform.

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| js | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| jvm | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |
| native | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second timeout; required completion/rejection/closure unestablished |

The corrected Huffman name has eight padding bits. These server deadlines do not establish whether the required connection error/closure occurs later. Do not transfer the confirmed client verdict across roles.

## h2-client-interaction-eager-server-settings

Case: `h2.client.interaction-eager-server-settings`. Current review: **confirmed**.

RFC review: [RFC 9113 §3.4](https://www.rfc-editor.org/rfc/rfc9113.html#section-3.4). Evidence: [N1, Scala 2.12.21](#native-scala-2-12-evidence).

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| native (Scala 2.12.21) | fail | confirmed mandatory deviation (MUST) | SETTINGS ACK immediately follows the 24-byte client preface, before initial non-ACK SETTINGS |

The fixture sends an empty server SETTINGS before reading any client octet.
The trace then receives the exact client preface followed by
`000000040100000000`: an empty SETTINGS frame with ACK set on stream zero.
RFC 9113 §3.4 requires acknowledgment of the server preface after sending the
client's complete connection preface, including its initial SETTINGS. This is
positive ordering evidence, independent of a deadline; no fixture fault was
established. Earlier Native Scala 2.13.18 and current JVM/JS cells passed as
recorded in N1's evidence section. The platform-wide ignore does not imply that
all Scala versions fail.

## h2-client-connection-window-overflow

Case: `h2.client.connection-window-overflow`. Current review: **provisional**.

RFC review: [RFC 9113 §6.9.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.1); [RFC 9113 §6.9.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-6.9.2). Evidence: [N1, Scala 2.12.21](#native-scala-2-12-evidence).

| Platform | Outcome | Reviewed classification | Observation |
|---|---|---|---|
| native (Scala 2.12.21) | inconclusive | provisional observation limit (MUST; no deviation established) | 8-second deadline after connection-window overflow stimulus; only SETTINGS ACK observed, rejection/closure unestablished |

After a valid handshake and zero-body request, the fixture sends connection
WINDOW_UPDATE increment 2147483647 (`0000040800000000007fffffff`). No DATA
has consumed the initial 65535-octet connection window, so the increment exceeds
the maximum allowed credit. The trace receives only SETTINGS ACK afterward;
the required termination remains unobserved at the eight-second deadline.
This establishes neither rejection nor a protocol deviation, and no fixture
fault was established. Earlier Native Scala 2.13.18 and current JVM/JS cells
passed as recorded in N1's evidence section. The platform-wide ignore does not
imply that all Scala versions fail.
