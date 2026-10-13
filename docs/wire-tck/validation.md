# Validation record

Generated audit and validation artifacts are not version-controlled and have been removed from this checkout. Artifact names and original revision IDs below are historical references, not available files or revisions in the rewritten branch. Run `sbt --client tckCheck` and `sbt --client tckCompare` to generate fresh results under `target/wire-tck-reports/`.

Current evidence: phase 4 matrix and audits (`audit/phase4-accepted-status.md`, historical artifact). The sections below
retain historical execution evidence; later snapshots do not rewrite earlier failures.

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

## Phase 3 pre-remediation repeat

Before review-derived source edits, the full catalog ran again on Scala 2.13.18.
All 3,008 outcomes reconciled: 2,477 pass, 470 assertion failures, 61 inconclusive,
with no missing events/outcomes or infrastructure errors. Evidence is retained in
the repeat archive (`validation/phase3-before-remediation-2.13.18.zip`, historical artifact).
Relative to phase 2, JVM `h2.server.stream-credit-boundary` changed from an
inconclusive deadline to pass, while Native `h2.client.goaway-unknown-error`
reproduced its previously recorded assertion failure. No other outcome category
changed. These observations do not resolve the fresh reviewers' oracle and
adapter findings or justify removing historical registry evidence.

Ten oracle challenges passed on JVM, JS and Native before remediation, including
new connection-versus-stream error-scope and DATA padding/flow-credit challenges
against actual catalog cases. Fresh independent review then found additional
false-pass and false-failure paths; their predicates and dispositions are in
the audit ledger (`audit/findings.md`, historical artifact). Phase-3 acceptance remains pending those
fixes, focused tests, fresh review, and full comparison on the corrected snapshot.

## Phase 3 first remediation comparison — acceptance failed

The formatted snapshot
`54d96bc0687624a656f2d29e72fb7408ab33815f:05f383fd025005f1fd5a648ef7729b12a44bc08e497b64c14bcd4c587c40799f`
produced all 3,008 case outcomes and all 16 suite completion markers under
Scala 2.13.18. The standalone `tckCompare` task exited 1: the newly introduced
MUnit listener misidentified fully qualified test names and recorded no per-case
events. Reconciliation correctly refused acceptance with 32 errors. These are
**diagnostic outcomes, not a validated conformance result**.

| Target/platform | Pass | Fail | Inconclusive |
|---|---:|---:|---:|
| Ember JVM | 594 | 142 | 16 |
| Ember Scala.js | 595 | 140 | 17 |
| Ember Native | 594 | 141 | 17 |
| Netty JVM | 696 | 43 | 13 |
| Total | 2479 | 466 | 63 |

The failed-acceptance evidence archive (`validation/phase3-remediated1-reconciliation-failed-2.13.18.zip`, historical artifact)
retains every manifest, result, transcript and reconciliation diagnostic. No case
reported infrastructure `error`, but fresh review identified unexercised counterexamples
where fixture errors or observation limits could still be misclassified. An absence
of recorded errors is therefore not infrastructure-correctness proof.

Independent harness checks passed on this run: 208 tests on JVM, 208 on Scala.js,
221 on Native; 10 Ember adapter tests per platform; 17 Netty adapter tests on JVM.
They include 94 H1 case-oracle and 35 H2 case-oracle challenges per platform.
Formatting actual-delta validation found no unexpected edits; scoped whitespace
checks passed. The final Scala 2.12/3 runtime matrix has not been run.

Compared with phase 2, the corrected Huffman-name-padding stimulus now exposes
`h2.client.hpack-reject-huffman-name-padding-eight` and
`h2.server.hpack-reject-huffman-name-padding-eight` failures on all Ember platforms.
These two exact IDs are pending registry integration; they are not silently
counted as passing. Three corrected H1 informational-presentation cases now pass
on all platforms and are candidates for removing obsolete ignores after the
remaining case review. Historical intermittent GOAWAY evidence remains applicable.
The registry still records the phase-2 snapshot while phase-3 acceptance is pending.

The audit ledger (`audit/findings.md`, historical artifact) records remaining findings and the workflow
stop: adapter provenance, outcome attribution and some RFC alternatives remained
unresolved after the first remediation cycle. No phase-3 commit was made, and
phase 4 has not started. Production code remains unchanged; nothing was pushed.

## A1 bounded adapter retry — focused validation only

The adapter retry passed 123 focused Scala 2.13.18 tests: 13 oracle and 20 Ember
adapter tests on each JVM/JS/Native, plus 24 Netty JVM adapter tests. Formatting
and exact mutation-envelope checks passed. Logs are local diagnostics:
`/private/tmp/wire-adapter-observer-jvm.log` and
`/private/tmp/wire-adapter-observer-platforms.log`.

Fresh independent JVM diagnostics nevertheless reproduced lost upload-finalizer
errors during cancellation against both real HTTP/2 adapters: normal success,
finalizer executed, no observer notification. A2 remains unresolved; these test
passes do not establish adapter correctness.

Scala 2.12 compilation passed for testkit and Ember on all three platforms after
a minimal Model.settings tuple-discard fix. Netty JVM compilation then failed
at NettyTarget.scala:418 on ExecutionContext.parasitic (not in Scala 2.12).
The remaining comparison compilations and Scala 3 were not reached. Evidence:
`/private/tmp/wire-adapter-retry-scala-matrix.log` and
`/private/tmp/wire-adapter-retry-scala-matrix2.log`. Scala 2.13.18 selection was
restored. No full comparison or Scala 2.12/3 runtime matrix was completed in A1.
See the final retry disposition (`audit/phase3-review-status.md`, historical artifact) for the persistent
finding and workflow stop. Phase-3 acceptance and phase 4 remain incomplete.

## A2 resumed validation — execution context

The user-requested F.executionContext replacement preserved the cancelable
promise bridge and removed its Scala2.13-only API use. Exact delta check passed.
The remaining Scala2.12 comparison modules compiled on JVM/JS/Native, completing
the earlier 2.12 compilation checks. The sbt server disconnected on entering
Scala3; a restarted run compiled all nine TCK module/platform test variants on
Scala3.3.8 successfully and restored Scala2.13.18. Logs:
`/private/tmp/wire-a2-matrix.log`, `/private/tmp/wire-a2-scala3.log`.
These checks precede completion of the resumed remediation batches and do not
establish their final matrix or runtime acceptance.

A2 focused batch checks (Scala2.13.18): source-owner adapters104 tests (Ember26
on each platform, Netty26 JVM), H1 oracle534 tests (178 each platform), H2/codec/
HPACK/inventory411 tests (137 each platform). Exact-file formatting and batch
delta checks passed. These do not establish acceptance: adapter fresh review
found two source lifecycle regressions subsequently assigned to a new narrowly
authorized batch; H1/H2 fresh reviews are pending. Runtime review supports
listener identity and anonymous-event fixes, but retains zero-execution harness
acceptance R1. No full-catalog comparison rerun is claimed.
Logs: `/private/tmp/wire-a2-adapter-terminal.log`,
`/private/tmp/wire-a2-h1-final2.log`, `/private/tmp/wire-a2-h2-jvm.log`,
`/private/tmp/wire-a2-h2-platforms.log`.

## A2 final diagnostic validation — reconciliation passed, acceptance blocked

Final source compiled all nine TCK module/platform Test/compile variants on
Scala 2.12.21 and 3.3.8. The combined compilation and Scala 2.13.18 full comparison
command exited 0 in 883 seconds. Log: `/private/tmp/wire-a2-validation.log`.
Runtime execution on Scala 2.12/3 has not been performed.

Catalog revision:
`54d96bc0687624a656f2d29e72fb7408ab33815f:21c10632d680472de9ab137fa0213cc63daf4b22e2ef478fe5aba35bcb21c73b`.
All 3,008 outcomes, 16 completion markers and 16 per-case MUnit event files
reconciled. Comparison applies no ignore registry. The corrected listener now
retains actual events; no reconciliation errors or infrastructure-error outcomes
were reported on this run.

| Target/platform | Pass | Fail | Inconclusive |
|---|---:|---:|---:|
| Ember JVM | 594 | 142 | 16 |
| Ember Scala.js | 595 | 140 | 17 |
| Ember Native | 593 | 142 | 17 |
| Netty JVM | 696 | 43 | 13 |
| Total | 2478 | 467 | 63 |

The diagnostic evidence archive (`validation/phase3-a2-diagnostic-2.13.18.zip`, historical artifact)
contains every manifest, outcome, transcript, MUnit event file and reconciliation
summary. This is **not phase-3 acceptance**: independently reproduced adapter,
case-classification, coverage and harness-gate defects remain. Zero recorded
infrastructure errors cannot disprove counterexamples not triggered by this run.
Failed/inconclusive cases are diagnostic outcomes, not uniformly confirmed target
deviations. The central registry remains the phase-2 snapshot pending trustworthy
phase-3 classification/integration; normal tckCheck was not rerun or certified.

Fresh review disposition is in the A2 audit report (`audit/phase3-a2-review-status.md`, historical artifact).
All reviewer/fixer work is complete. Repeated findings require the canonical stop;
no additional source remediation, phase-3 commit, phase-4 audit or push occurred.
Current inventory has 1,784 rows: 924 covered, 708 gaps, 152 excluded. Those counts
are classifications, not proof of exhaustive RFC obligation coverage.

Final scoped Scala formatting checks, sbt formatting, generated workflow check
and whitespace checks passed. Log: `/private/tmp/wire-a2-final-checks.log`.
Baseline-through-working-tree tracked-path audit found no changes outside the
TCK frontier. No build process or subagent task remains pending.

## A3 authorized remediation and fresh review (2026-10-11)

The user authorized fixing the ten remaining findings. Three fresh file-owned
fixers and the coordinator implemented bounded adapter, H1, H2 and execution-gate
changes under the frozen charter. Four fresh reviewers then inspected the original
scope plus accepted deltas. No Ember production changes were made.

Focused final checks on Scala 2.13.18:

- Adapter lifecycle/reuse: Ember 38 on JVM/JS/Native and Netty 31 JVM (145 total).
- H1 oracle: 261 on each platform; JVM codec 54 (837 total).
- H2/codec/HPACK/catalog selection: 173 each JVM/Native; initial JS 172/173 with
  reset/PING byte-budget timeout, followed by unchanged H2 rerun 92/92. This is
  not stability proof and omitted OracleChallengeSuite from the selection.
- Execution-gate selfchecks, exact-file formatting and actual-delta audits pass.

The first full comparison lost its sbt server after 440 outcomes and 2 completed
suites. No JVM crash report established a cause. Its partial evidence and log
are retained in the A3 diagnostic archive; it is not a completed comparison.
One fresh attempt outside the sandbox completed in 430 seconds:

| Target | Pass | Fail | Inconclusive | Total |
| --- | ---: | ---: | ---: | ---: |
| JVM Ember | 594 | 142 | 16 | 752 |
| JVM Netty | 696 | 43 | 13 | 752 |
| JS Ember | 595 | 140 | 17 | 752 |
| Native Ember | 594 | 141 | 17 | 752 |
| Total | 2479 | 466 | 63 | 3008 |

All 16 catalog suites completed. Every target has the same 752 case IDs; manifests,
per-case MUnit events and outcome records reconcile. Each Ember adapter harness
produced 38 unique successful events after preparation removed direct-test residue.
Catalog revision:
`54d96bc0687624a656f2d29e72fb7408ab33815f:a521911e7ca5a675a444e0e2724252b166fc3c20c05d1eb1a4e12110d7d2daa2`.

**The comparison command failed acceptance, as required.** The full harness
exposed the stale stream-only HPACK error-scope mutation on all platforms:
it expects ProtocolFailure from observation exhaustion, which now correctly
produces WireObservationLimit. JS also timed out at 30 seconds in both the H1
incomplete-request body observation-limit challenge and the H2 terminal-PING
byte-budget/reset challenge. These are unresolved harness/stability failures,
not Ember protocol deviations and not ignore-registry candidates. All other
recorded full-run gate errors are consequences of those failing harness suites;
there were no missing-case, event-identity or adapter-execution gate errors.

Evidence: A3 diagnostic archive (`validation/phase3-a3-diagnostic-2.13.18.zip`, historical artifact).
Full fresh-review disposition and nine important concerns:
audit/phase3-a3-review-status.md (`audit/phase3-a3-review-status.md`, historical artifact).
The independent effect-level cleanup probe returns false success on both JVM
adapters despite the focused FS2-scope cancellation regressions passing.

Repeated provenance, observer-limit and coverage predicates trigger the canonical
review-fix stopping rule. No further remediation iteration or phase acceptance
commit was made. Registry reconciliation, ordinary green runs, stability,
Scala 2.12/3 runtime execution and phase 4 RFC audits remain incomplete.

Final version checks: all nine module/platform Test/compile combinations pass
on Scala 2.12.21 and 3.3.8. Scala 3 initially rejected the new Netty fixture's
wildcard shutdown future passed by name; a local val inside IO.defer fixes
that compile-only issue while preserving deferred shutdown. NettyTargetSuite 31
then passes on Scala 3.3.8, 2.12.21 and 2.13.18. The full comparison archive above
predates only this mechanical fixture correction; it retains its original
catalog revision. No protocol case or expected outcome changed afterward.
The broader 2.12/3 conformance runtime matrix remains unexecuted.

Final scoped Scala/test formatting, scalafmtSbtCheck, root/githubWorkflowCheck
and whitespace checks pass. Logs and the independently compiled cleanup probe
are included under `_validation/` in the diagnostic archive.

## A4 continuation: diagnostic comparison (2026-10-11)

A4 authorizes continued remediation. Adapter provenance, Netty head completion,
H1 recovery/framing, H2 trailers and several observation bounds were corrected;
focused checks and fresh reviews are recorded in audit/phase3-a4-review-status.md.
The latest adapter partition review reports no important unresolved concern.

| Target | Pass | Fail | Inconclusive | Total |
| --- | ---: | ---: | ---: | ---: |
| JVM Ember | 594 | 142 | 16 | 752 |
| JVM Netty | 696 | 43 | 13 | 752 |
| JS Ember | 595 | 140 | 17 | 752 |
| Native Ember | 593 | 142 | 17 | 752 |
| Total | 2478 | 467 | 63 | 3008 |

All 16 catalog suites completed. Revision:
`54d96bc0687624a656f2d29e72fb7408ab33815f:d6dabecbe1edc190ba14d04a03e9047220a8cabfcbf0b10df1a5d9d893fd9d1c`.
The command correctly exits1: a JVM H1 interaction oracle assertion did not
preserve the expected second-operation error identity. Its cause is under
investigation, not an Ember protocol deviation or ignore candidate.

The A4 diagnostic archive (`validation/phase3-a4-diagnostic-2.13.18.zip`, historical artifact) preserves
all outcomes, including registered cases. These results are not phase acceptance:
fresh review additionally reproduced legal request-trailer false failures, an
HPACK interval-minimum omission, generic SETTINGS-dependent invalid stimuli,
and suppressed/deep timeout false passes. Exact before probes and bounded
remediation capabilities are retained in audit/. Registry reconciliation and
normal/full-matrix acceptance await correction and fresh validation.

### Second A4 diagnostic comparison

After the completed settings, trailer, client-side coordination and Netty
ordering corrections, the full command completed in 405 seconds with exit 0.
All 16 catalog suites and all harness gates passed; each target produced the
same 752 case IDs.

| Target | Pass | Fail | Inconclusive | Total |
| --- | ---: | ---: | ---: | ---: |
| JVM Ember | 595 | 141 | 16 | 752 |
| JVM Netty | 700 | 39 | 13 | 752 |
| JS Ember | 596 | 139 | 17 | 752 |
| Native Ember | 594 | 141 | 17 | 752 |
| Total | 2485 | 460 | 63 | 3008 |

Revision: `54d96bc0687624a656f2d29e72fb7408ab33815f:7907c7b1ca71ce1a3b284eb5923dfb1032d0d60b58a417e35c6ffe859fab85d0`.
Complete evidence (`validation/phase3-a4-second-diagnostic-2.13.18.zip`, historical artifact).
Full independent harness runs also pass on Scala 2.13.18: 898 JVM, 898 JS,
911 Native. Netty adapter tests pass 51 cases on all three Scala versions.

**A green diagnostic gate is not phase acceptance.** Concurrent fresh review
reproduced the reverse scripted-peer cleanup race, additional negotiated
concurrency/header-list/HPACK-minimum boundaries, and incomplete tracking-link
validation for multiline registry entries. Their independent counterexamples
are retained in the audit record. These remain TCK work, not Ember production
fixes or ignore candidates. Registry reconciliation, full supported-version
runtime checks, and phase 4 remain outstanding.

### Current A4 review checkpoint (not phase acceptance)

The current catalog contains 750 cases. A subsequent full Scala 2.13.18
comparison recorded 2,997 of the expected 3,000 case/target outcomes:
2,372 passes, 348 failures, 221 inconclusive results, and 56 infrastructure
errors. Reconciliation correctly failed. The 56 errors were fourteen bounded
HTTP/2 framing cases blocked by an overly restrictive field-size prerequisite;
three further cases lacked reports after outer test timeouts. Eight JVM harness
controls also failed because an added SETTINGS frame invalidated their scripted
write ordinals. These are TCK failures, not Ember deviations.

The comparison archive (`audit/phase3-a4-peer-push-comparison-2.13.18.zip`, historical artifact) and
execution evidence (`audit/phase3-a4-peer-push-comparison-execution.zip`, historical artifact) retain
that unsuccessful snapshot. Isolated reruns of the three missing cases all
produced outcomes: two passes and one inconclusive result; see the
follow-up evidence (`audit/phase3-a4-missing-outcomes-followup.zip`, historical artifact). Those reruns
do not turn the incomplete full run into complete-catalog evidence.

The JVM fixture correction passes its nineteen tests on Scala 2.12, 2.13 and 3,
with independent replay and mutation review. The field-size correction passes
123 focused controls on each JVM Scala version and on Scala.js and Native;
its fresh review and a new full comparison are ongoing. Scala.js checks use
upstream Node 24.20.0 on macOS, the release selected by the pinned Nix inputs,
not the exact patched Nix/Linux runtime. Full comparison, registry reconciliation,
final supported-matrix checks and phase 4 remain outstanding.

Independent cross-validation attributes 67 Netty client failures to its native
codec emitting an odd maximum Last-Stream-ID in error GOAWAY frames. Native-only
probes reproduce the behavior without the TCK adapter. The
adjudication evidence (`audit/phase3-a4-netty-goaway-validation.zip`, historical artifact) supports
retaining the RFC-derived oracle; it does not authorize repairing the target.


### Finite-header prerequisite repair: full comparison checkpoint

The frozen Scala 2.13.18 comparison at catalog revision
`54d96bc0687624a656f2d29e72fb7408ab33815f:d4d13cba50cb3864464e57526391d52ffbfe46a75645f1662015008b7acea85c`
recorded 2,999 of 3,000 selected case/target outcomes: 2,409 pass, 366 fail,
224 inconclusive, and zero error outcomes. JVM Ember and Scala.js Ember each
recorded 595 pass / 90 fail / 65 inconclusive; Native Ember recorded
595 / 89 / 65 with one missing outcome; Netty recorded 624 / 97 / 29.
The missing Native case, `h2.client.reject-trailers-without-end-stream`, reached
MUnit's outer timeout (16.004 seconds) before the case report was written.
The reconciliation check correctly blocked validation. The cause is not yet
established; other unusually long wall-clock measurements are not evidence of
a specific scheduler or host failure.

All full testkit suites passed in this run: JVM 4,982, Scala.js 5,023, and
Native 4,976. These harness results do not make the incomplete comparison an
accepted run. Evidence is preserved in
`audit/phase3-a4-finite-header-comparison-2.13.18.zip` and
`audit/phase3-a4-finite-header-comparison-execution.zip`.
The earlier disconnected attempt is retained in the execution archive too.

The HTTP/1 early-upload prerequisite repair is now authorized for its exact
three-file batch. The six HTTP/2 flow-control prerequisites remain under
adjudication: waiting for a sender to exhaust every byte of available credit
is not a wire-protocol requirement. Neither test defect is an Ember deviation.
Phase 3 remains unaccepted and uncommitted.


### Native missing-outcome diagnostic

A temporary suite repeated the unchanged registered
`h2.client.reject-trailers-without-end-stream` case 100 times on Native,
including the real WireReport initialize/record/complete path. All 100
underlying outcomes were protocol failures (the malformed response was
consumed), each recorded successfully in 6–48 ms. The 100 diagnostic assertions
passed; these are not 100 conformance passes. The original 16.004-second MUnit
timeout did not recur and its cause is unestablished. All 17 inspected source
hashes were unchanged, and temporary sbt source settings were restored.
Evidence: `audit/phase3-a4-native-missing-outcome-evidence.zip`, including the
original Native timeout XML and all repeated outcome reports.

Host power logs show a later 985-second sleep during the Netty tail of the full
run; that interval was after the Native client suite completed and does not
explain the Native missing outcome. No timeout increase or production change
is supported by this diagnostic. The incomplete full run remains blocked;
a new reconciled full run is required.


### Flow prerequisites and empty-TE oracle correction

A4FC1 changed six flow-control cases to measure remaining credit rather than
require exact exhaustion. The retained differential now accepts both whole-frame
and tail-splitting senders. The full JVM 2.13 oracle/catalog run passed 4,176
tests; JVM 2.12 and 3 each passed 52 selected controls/challenges, and JS/Native
each passed 68. All thirteen other complete-body reads and prior oracle
fixtures remained unchanged. Evidence: `audit/phase3-a4-flow-prerequisite-evidence.zip`.

A4TE1 deliberately retired `h2.server.reject-te-empty`: the reviewed normative
text does not resolve whether the zero-member TE list is covered by HTTP/2's
trailers-only exception. Mandatory acceptance is not asserted either. The
current catalog is 749 cases (266 HTTP/1.1, 483 HTTP/2); historical 750-case runs
remain historical evidence. Supported non-trailers and response-TE cases remain.
The retirement passed 183 admission controls and six catalog checks on each of
JVM Scala 2.12/2.13/3 and JS/Native Scala 2.13, with compilation, formatting,
header and integrity checks. Evidence: `audit/phase3-a4-empty-te-retirement-evidence.zip`
and the companion `audit/phase3-a4-empty-te-retirement-fix-report.md`.

Exact mutation boundaries for both batches passed coordinator verification.
A fresh combined review is pending; neither focused matrix establishes phase
acceptance. The distinct H1 opaque-field prerequisite repair is also pending.


### A4 flow/TE/upload complete comparison checkpoint

The 2.13.18 full comparison recorded all 2996 outcomes (749 IDs × four targets), with no missing or error outcomes: Ember JS 595 pass/90 fail/64 inconclusive; JVM 593/89/67; Native 592/91/66; Netty JVM 623/97/29. Revision 54d96bc0687624a656f2d29e72fb7408ab33815f:c53ffae541df499791de7494e94a51b53cfcddf07353079964009d7f84d4060f. This run remains blocked: JS HPACK separator client/long-internal-tab and Native complete-fixed-length-at-eof assertion-provenance harness tests timed out. Host logs record 908 seconds of thermal-emergency sleep overlapping those suites; this is correlation, not proof of the exact failing stage. Both exact tests passed in isolated follow-up through normal sbt platform runners, with unchanged deadlines. Follow-up is archived separately and does not turn the original blocked run green. Comparison and execution archives: phase3-a4-flow-te-upload-comparison-2.13.18.zip and -execution.zip; follow-up: phase3-a4-flow-te-upload-timeout-replay.zip under audit/. Two fresh State oracle findings remain under remediation; outcomes are not phase acceptance.


### A4 State independent-credit and no-push repair checks

A4NC1/A4SP1 repair changed exactly State main cases/helper, focused H2 oracle controls, and related State inventory notes. Actual-delta validation found no unexpected edits; concurrent audit copies were coordinator-owned. Full JVM2.13.18: 4207 H2 oracle +6 catalog +14 challenge tests passed (4227). Focused JVM2.12.21/3.3.8 and JS/Native2.13.18 each passed112. Both negative-window IDs passed all8 target/platform/role comparison cells after repair; these selected runs do not establish a complete comparison. All source hashes stayed frozen through formatting/header and restored sbt sessions. Evidence including initial red controls, initial fixture assertion corrections, exact commands and final report: audit/phase3-a4-state-credit-push-evidence.zip. Fresh independent review and full-catalog reconciliation remain pending.


### A4 completion-padding repair and selected comparison

A4CP1 provisions bounded completion credit across all13reads/12IDs; A4CP1E adjusts one existing challenge fixture to the new legal grant exchange. Exact3+1 file-delta checks show only authorized source changes plus coordinator-owned audit updates. Full JVM4226 H2oracle and6catalog passed; original challenge13/14 fixture failure preserved, followed by authorized repair and14/14 pass. Focused131 each passed on JVM2.12.21/3.3.8 and JS/Native2.13.18. A copied-source retained-credit mutant fails both exact4096-frame completion controls at payload271/remaining270. No deadline/bound increases.

Selected comparison at revision54d96bc0687624a656f2d29e72fb7408ab33815f:66b8934ace465bc7ed58d12391e20428ac1086e1be387baefb4b26c6da7da35e reconciles48freshcells:42pass,2fail,4inconclusive. Nettyall12pass; Embereachplatform10/12pass. Serverconnection-credit-independent has positive overspend JS/Native and JVMtimeout; clientcase timesoutall3. JS/Native comma-separated tags executedzero; those attempts/stalecopies are segregatedinvalid. Corrected single-ID executions eachprove count1/freshrevision and immediate capture before suite-report cleanup. Selected manifests are explicitly notfullsuitecompletion. Exact format/header/defaultrestoration and frozenhash checks passed. Evidence: audit/phase3-a4-completion-padding-evidence.zip. Fresh review and full-catalog/registry acceptance remain pending.


### A4 completion-trailer fixture and registry refresh

The stream-credit trailer fixture now waits for stream 1 WINDOW_UPDATE before
sending the remaining body. All 16 native JVM controls pass. Eight instrumented
positive controls consume sufficient stream credit; removing both stream grants
from a copied-source mutant changes eight historical false passes to eight
existing 30-second starvation timeouts. These mutation timeouts demonstrate the
missing prerequisite, not protocol rejection. Exact formatting/header and delta
checks passed. Evidence: fixture repair (`audit/phase3-a4-completion-trailer-fixture-evidence.zip`, historical artifact).

The adjudicated registry now has 151 entries: 90 confirmed and 61 provisional, with
three additions and eleven removals. Mandatory deviations, recommendations and
observation limits remain distinct, with history preserved. CatalogSuite 6/6,
tckIntegrity, exact formatting/header and delta checks pass. Evidence:
registry refresh (`audit/phase3-a4-registry-refresh-evidence.zip`, historical artifact). Both batches
await fresh postreview; normal/full comparison and phase acceptance remain open.

The fresh State/trailer review is complete with no unresolved important finding.
All 13 completion reads and 20 handshake callers were inspected; independent
padding, negative-window and retained-credit probes support the repair.
Sealed review (`audit/phase3-a4-completion-trailer-fixture-postreview.md`, historical artifact).

The fresh registry review independently reconciled all entries and supporting
full/selected archives, preserved the historical records, and confirmed comparison
bypass and missing-outcome rejection. No new important finding.
Sealed review (`audit/phase3-a4-registry-refresh-postreview.md`, historical artifact).


### A4 full comparison after completion and registry repairs

The Scala 2.13.18 `tckCompare` run completed with exit 0 and all 2,996 outcomes
for 749 IDs across Ember JVM/JS/Native and Netty JVM. All 16 protocol suites
finalized; reconciliation found no missing/error outcomes or harness blocker.
The source freeze remained unchanged. Aggregate results: 2,420 pass, 367 fail,
209 inconclusive. Each Ember platform had 599 pass, 90 fail, 60 inconclusive;
Netty had 623 pass, 97 fail, 29 inconclusive. There were no unregistered Ember
nonpasses. Native eager-zero-window-upload passed this time but retains its
previous positive intermittent deviation. JVM server connection-credit now
positively overspends by one octet, superseding its earlier inconclusive result
without deleting that history; final registry adjudication is pending.

The complete harness ran without failures: JVM 5,084, JS 5,125, Native 5,078
tests, with no skips. These totals exclude stale XML from temporary selected
probe suites. The three Ember adapter suites each passed 55 tests, and Netty
passed 51. Runtime settings included Node 24.20.0 for Scala.js, JVM Java 27,
and a temporary build-task concurrency cap of two. Deadlines were unchanged.
The invocation took 1,633 seconds. Earlier blocked full runs remain historical
failures; this is a separate complete execution, not a relabeling of them.

Evidence: full reports (`audit/phase3-a4-registry-completion-comparison-2.13.18.zip`, historical artifact),
command/log/source freeze (`audit/phase3-a4-registry-completion-comparison-execution.zip`, historical artifact),
and current harness XML (`audit/phase3-a4-registry-completion-harness-xml.zip`, historical artifact).
Normal Ember runs, final metadata review and phase acceptance remain pending.


### A4 normal Scala 2.13.18 runs and static checks

`sbt --client tckCheck` completed with exit 0 on JVM, JS and Native. The full
harness passed again: 5,084 / 5,125 / 5,078 tests respectively. Normal protocol
outcomes were JVM 599 pass / 150 skipped, JS 599 / 150, Native 598 / 151, plus
55 passing Ember adapter tests on each platform. No ignored case counts as
passing. The extra Native skip retains the earlier intermittent upload witness.
All current manifests contain exactly 749 cases and every listed case has a
fresh outcome. An initial directory-wide count found three retired phase-2
artifacts on each platform; those nonmembers are explicitly excluded from the
current archive and its census. The records were not counted as current passes.
The complete invocation took 1,119 seconds; source hashes remained unchanged.
Normal-run evidence (`audit/phase3-a4-normal-check-2.13.18.zip`, historical artifact).

Repository `headerCheckAll`, `scalafmtCheckAll`, `scalafmtSbtCheck` and
`githubWorkflowCheck` all passed after restoring build defaults.
Static-check log (`audit/phase3-a4-static-checks.zip`, historical artifact).
Scala 2.12 and Scala 3 normal checks, final provenance review, the phase-3
checkpoint and phase-4 specification audits remain outstanding.

The final history/registry review resolves A4RG3 with no remaining correctness
finding. It independently verifies both full comparison and current normal-run
archives. Sealed review (`audit/phase3-a4-history-wording-postreview.md`, historical artifact).


### A4 initial Scala 2.12.21 normal run — two Native observations

All three complete harnesses passed with the same 5,084 / 5,125 / 5,078 totals.
JVM and JS each passed 599 protocol cases plus 55 adapter tests with 150 skips.
Native passed 596 protocol cases plus 55 adapter tests, skipped 151, and recorded
two nonpasses: eager-server-settings positively emitted SETTINGS ACK before the
required initial non-ACK SETTINGS; connection-window-overflow reached its
8-second deadline after the overflow stimulus, without an observed connection
error. The latter remains inconclusive, not proof of a protocol violation.
All 749 cases per platform produced current manifest outcomes. Exit code 1 and
all original diagnostics are retained in the
initial Scala 2.12 archive (`audit/phase3-a4-normal-check-2.12.21-initial.zip`, historical artifact).
No harness defect was established by source/trace inspection; A4RG4 records the
new Native observations with their Scala-version evidence. Earlier Scala 2.13
passes remain preserved. Follow-up ordinary runs remain pending; this failed
invocation is not relabeled as successful.


### A4 Scala 2.12.21 ordinary follow-up

After recording the two Native observations, the three ordinary Ember suites
passed: JVM and JS each have 599 passing protocol cases and 150 registered
skips; Native has 596 passing cases and 153 registered skips. All three adapter
suites passed 55 tests. Every platform manifest contains all 749 current IDs,
with one fresh catalog revision and no source drift. The complete harnesses
were not repeated for this metadata-only change: the archive carries their
passing XML from the initial full run (with the subsequently repeated JVM
catalog integrity suite). This follow-up ran only the three wire-tck test tasks.
Follow-up evidence (`audit/phase3-a4-normal-check-2.12.21-followup.zip`, historical artifact).

The fresh registry review cleared A4RG4 without additional findings and
independently verified all 153 IDs / 453 designated platform cells.
Sealed review (`audit/phase3-a4-native-matrix-registry-postreview.md`, historical artifact).
The initial failed invocation remains preserved separately. Full Scala 3
normal validation is underway; phase 3 remains uncommitted.


### A4 completed Scala 3.3.8 normal validation

The full Scala 3.3.8 `sbt --client` invocation of `tckCheck` completed with exit 0
on JVM, JS and Native, using the current 153-entry registry. All twelve protocol
manifests and 2,247 current outcomes reconcile: 749 IDs per platform, JVM/JS each
599 pass / 150 registered skips, Native 596 pass / 153 registered skips. All
three Ember adapter suites passed 55 tests. The complete harnesses ran afresh:
JVM 5,084 / JS 5,125 / Native 5,078 passes, with zero failures, errors or skips.
The source freeze had no drift; the recorded revision is
`54d96bc0687624a656f2d29e72fb7408ab33815f:e2b83fc267ec5e84a00457cd595b45f443423a4655949342bd193c7c7f5eabdf`.

Completed command, log, source freeze, reports and XML (`audit/phase3-a4-normal-check-3.3.8.zip`, historical artifact),
SHA-256 `bb2aa57646a8455cfcfc0ed802ddbafed606a7696fe47f8c106d11a318168567`,
were independently reconciled in the checkpoint review (`audit/phase3-checkpoint-review.md`, historical artifact)
and its sealed evidence (`audit/phase3-checkpoint-review.zip`, historical artifact). The archived command
records the local Node override and temporary concurrency cap of two; deadlines
were unchanged. This is completed local validation, not a remotely dispatched CI job.

### Phase 3 checkpoint and remaining boundaries

The supported Scala/platform compilation and normal runtime matrix is supported
by the retained evidence: full normal Scala 2.13.18 passed with the earlier
151-entry registry; Scala 2.12.21 passed ordinary follow-up after the two Native
observations were recorded; full normal Scala 3.3.8 passed with 153 entries.
The Scala 2.12 initial failed invocation is preserved. Its follow-up reused
35 of 36 harness XML files and repeated JVM CatalogSuite, as documented above;
it was not a fresh full harness run. The latest Native suppressions apply across
Scala versions. No full normal Scala 2.13 rerun with the 153-entry registry or
full comparison on Scala 2.12/3 is claimed.

The completed Scala 2.13 comparison remains 2,996 cells / 749 IDs / 16 suites:
2,420 pass, 367 fail and 209 inconclusive, without infrastructure blockers.
Earlier failed runs retain their original status. The current registry is
153 IDs (92 confirmed / 61 provisional), covering 453 exact platform cells.
See the current checkpoint (`audit/phase3-accepted-status.md`, historical artifact) for classifications,
selected oracle-mutation and bounded stability evidence, API scope, the optional
harness-member census limitation and links to the fresh review history.

Header, Scala/SBT formatting and generated-workflow checks passed separately.
Scoped current-diff whitespace checks passed; plain B0-through-working-tree
`git diff --check` still reports 167 trailing-tab warnings from the TSV format's
empty final fields. No blanket whitespace-clean claim is made.

At this historical checkpoint, phase 3 documentation postreview and the local
commit were pending, and phase 4 had not started. Phase 3 subsequently completed
and was committed as `4dcb21681c71a013025ba4a4a140a63b4a781ee7`. The
phase 4 record (`audit/phase4-status.md`, historical artifact) documents the subsequent RFC-first
bidirectional audits. No production change, remote CI dispatch or push occurred.


## Phase 4 final execution matrix

The final six invocations completed on one frozen revision across Scala 2.12.21,
2.13.18 and 3.3.8. Every normal run freshly executed all harness and adapter tests
and 749 protocol cases per Ember platform, counting registered skips separately.
Every comparison executed 749 cases on each of Ember JVM/JS/Native and Netty JVM,
bypassing all ignores. Every case/target outcome category matches across the three
Scala versions. No current cases, harness members or manifests are missing.

Per normal run: JVM/JS each 599 protocol passes and 150 skips; Native 596 passes
and 153 skips. Per comparison: each Ember target 599 pass / 90 fail / 60
inconclusive; Netty 623 pass / 97 fail / 29 inconclusive. Every invocation passes
6,388 JVM, 6,432 JS and 6,402 Native harness tests, with 55 Ember adapter checks
per platform; each comparison also passes 66 Netty adapter checks.

The phase 4 evidence record (`audit/phase4-accepted-status.md`, historical artifact) includes all commands,
settings, nine runtime archives and hashes, the machine-readable matrix, current
format/header/integrity/workflow checks, failed attempts and fresh audit history.
This is local validation, not a remote CI result or a claim of comprehensive RFC
conformance. The inventory still records 752 explicit gaps. The final independent reporting audit (`audit/phase4-final-reporting-audit.md`, historical artifact)
cleared acceptance; the current evidence record is the checkpoint for the
authorized local phase commit.
