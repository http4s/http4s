# Validation record

Generated audit and validation artifacts are not version-controlled and have been removed from this checkout. Artifact names and original revision IDs below are historical references, not available files or revisions in the rewritten branch. Run `sbt --client tckCheck` and `sbt --client tckCompare` to generate fresh results under `target/wire-tck-reports/`.

Baseline commit: `7ae5b1ea14c6df3f9b6b00ba5a74b2ff7adcde2d`.

## Phase 1

Executed on 2026-10-10, macOS arm64, Java 27, Node.js 26.10.0, Scala 2.13.18.
`sbt --client tckCheck` passed on JVM, Scala.js and Scala Native: each platform
executed 62 harness tests (including RFC/external HPACK vectors and faulty peers),
six Ember adapter lifecycle tests and eight protocol cases; the ninth protocol
case, missing Host, was centrally ignored. `sbt --client tckCompare` completed all 36 outcomes: 32 passed and four failed
(the missing-Host case for each target). No cases were suppressed and all manifests
reconciled with MUnit events and report metadata. The eight Netty adapter tests
also passed. Recorded outcomes and transcripts (`validation/phase1.jsonl`, historical artifact) retain
the exact catalog revision, settings and per-target evidence.

Individual selection was exercised with
`sbt --client 'wire-tck/testOnly org.http4s.wire.EmberHttp1ServerSuite -- --tests=h1.server.get'`:
exactly one case ran. Intentional comparison failures point at the case definition
in `BaselineCases.scala`, preserving MUnit caller locations. Oracle challenges
detect changed PING payloads, corrupt content and wrong message boundaries for
the intended assertion; resource tests cover cancellation and partial consumption.

Targeted formatting/header checks and `root/githubWorkflowCheck` passed. No Ember
production changes were made. The Scala 2.12/3 matrix is deferred to phase 2;
phase-1 checks establish the scaffold on all platforms, not broad RFC coverage.
The JS abandoned-upload server cleanup observation remains explicitly documented
in [known-failures.md](known-failures.md).

## Phase 2 integration checkpoints

The first HTTP/1 authoring wave expanded the catalog to 256 cases (252 HTTP/1
and four initial HTTP/2 cases). On Scala 2.13.18, the complete comparison produced
1,024 outcomes: 911 passed, 101 failed assertions, and 12 were inconclusive.
There were no infrastructure-error outcomes or missing cases. Each Ember platform
produced 227 passes, 27 assertion failures and two inconclusive outcomes; Netty
produced 230 passes, 20 failures and six inconclusive outcomes. The exact snapshot,
manifests, versions and transcripts are in
the checkpoint archive (`validation/phase2-h1-2.13.18.zip`, historical artifact).

All 29 nonpassing Ember case IDs were added provisionally to the central registry.
The subsequent `tckCheck` passed on JVM, JS and Native: each platform ran 64 harness
tests, six adapter tests and 227 passing protocol cases, with 29 registered skips.
The provisional entries include recommendations and unresolved API/observation
questions; they are not all confirmed mandatory protocol defects.

This checkpoint does not validate the subsequently authored HTTP/2 cases or finish
the phase. Full-catalog validation and fresh adversarial audits remain pending.

## Phase 2 full-catalog validation

The completed authoring waves produced 752 cases (266 HTTP/1.1 and 486 HTTP/2).
The independent inventory contains 1,780 detailed rows and explicitly retains 695
coverage gaps; these counts do not establish complete RFC conformance. All 542
section headings are classified.

On Scala 2.13.18 the complete comparison reconciled 3,008 case/target outcomes:
2,477 passed, 469 failed assertions and 62 were inconclusive. No infrastructure
errors, missing MUnit events, missing outcomes or suppressed tests remained.
The complete evidence archive (`validation/phase2-2.13.18.zip`, historical artifact) contains manifests,
versions, settings, references and bounded transcripts for every outcome.

| Target | Passed | Assertion failures | Inconclusive |
|---|---:|---:|---:|
| Ember JVM | 592 | 143 | 17 |
| Ember Scala.js | 594 | 141 | 17 |
| Ember Scala Native | 594 | 141 | 17 |
| Netty JVM | 697 | 44 | 11 |

The central registry contains 160 individually tracked Ember case IDs. Entries
remain provisional pending phase-3 RFC and adapter review. Two GOAWAY observations
also retain earlier intermittent Native evidence even though the latest Native
run passed; the supplemental record (`validation/phase2-intermittent-goaway.jsonl`, historical artifact)
contains their original outcome metadata and transcripts. An inconclusive deadline
is not a confirmed implementation deviation.

Integration corrected TCK defects before classifying failures: Native reset
recognition now preserves received bytes and distinguishes a frame-boundary close
from truncated headers or payloads; the Ember adapter observes HTTP/2 :path
without prefixing its separately represented scheme. This removed 63 apparent
failures per platform. A regression checks escaped path bytes and an empty query
boundary on all Ember platforms. MUnit failure messages include only the first
4,096 transcript characters plus the full artifact path; this restored two lost
Native runner events for large traces. The archive retains the full bounded trace.

The focused codec checks passed (37 JVM cases, 50 Native cases including 13
Native reset checks). All seven Ember adapter tests passed on every platform,
and all eight Netty adapter tests passed during comparison.

The final `sbt --client tckCheck` passed on all three platforms. JVM and Native
each ran 592 passing protocol cases and reported 160 registered skips; Scala.js
ran 594 passing protocol cases and reported 158 skips. The two extra Native
skips are the explicitly retained intermittent GOAWAY observations. Each platform
also passed seven Ember adapter tests; the harness passed 71 cases on JVM and JS,
and 84 on Native (including the 13 Native-specific reset checks).

All three modules' test sources compile on JVM, JS and Native with Scala
2.12.21, 2.13.18 and 3.3.8, including the JVM Netty adapter and tests. Targeted
`headerCheckAll`, `scalafmtCheckAll`, `scalafmtSbtCheck`, and
`root/githubWorkflowCheck` passed. Phase-2 runtime acceptance uses Scala 2.13.18;
Scala 2.12/3 execution of the final audited suite remains a phase-4 check. No
remote CI job was dispatched and no Ember production source was changed.
