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

## Later phases

RFC authoring, full-catalog Netty validation and fresh adversarial audits are pending.
