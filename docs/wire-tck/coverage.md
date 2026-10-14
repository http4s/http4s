# Coverage inventory

Generated audit and validation artifacts are not version-controlled and have been removed from this checkout. Artifact names and original revision IDs below are historical references, not available files or revisions in the rewritten branch. Use the ordinary test commands in [Running](README.md#running) and `sbt --client tckCompare` to generate fresh results under `target/wire-tck-reports/`.

The inventory and executable catalog answer different questions. Inventory rows
classify obligations, alternatives, grammar productions and exceptions derived
from the RFCs. Catalog manifests identify the concrete scenarios that actually
ran. A covered inventory row does not mean every possible input was tested, and
a passing scenario does not establish conformance to its entire RFC section.

The [section index](sections.tsv) classifies every heading in the four baseline
RFCs, independently of test discovery. The detailed [inventory fragments](inventory/)
separate client/server obligations and label each row `covered`, `gap`, or
`excluded`. `covered` requires specific executable case IDs; `gap` identifies
missing variants or observations; `excluded` explains a scope boundary or delegates an applicable obligation to another inventory fragment.
`section-gaps.tsv` retains any section whose obligations have not yet been expanded.
Those placeholders explicitly prevent a comprehensive-inventory claim.

`Catalog.all` combines the disjoint authoring shards. Each suite emits the same
applicable case IDs into a manifest before execution. Integrity checks reject
duplicate IDs, unknown case references, unmapped cases and applicable sections
without an obligation or explicit gap. These are consistency checks; independent
specification audits must still challenge whether a row accurately states the RFC
and whether its cited cases establish the claimed observation.

For a manual audit, start with an inventory row's RFC number and section, follow
the matching direct URL beside its case, and compare the visible preconditions,
stimulus and assertion. Then inspect the outcome and wire transcript. `MUST`,
`SHOULD`, permissions and syntax rules retain distinct labels. Recommendation
failures, unresolved interpretations, infrastructure errors and inconclusive
deadlines are not interchangeable with confirmed mandatory protocol violations.

The [validation record](validation.md) records exact executed snapshots. The
[remaining-failure list](known-failures.md) records Ember observations; registry
skips are separate from requirement gaps and never count as passing coverage.

## Phase 2 inventory snapshot

The historical phase-2 catalog contained 752 individually registered cases:
266 HTTP/1.1 and 486 HTTP/2, including HPACK and cross-section interactions. The
independent section index classified 542 headings. Its 1,780 detailed inventory
rows included 933 mapped to cases, 695 explicit coverage gaps, and 152 scope exclusions.
These rows are not equally sized obligations and are not a conformance percentage.
No applicable section remains an unexpanded section placeholder; this is an
indexing check, subject to the independent phase-4 specification audit.

The recorded gaps include sender-side grammar variants, observations unavailable
through the target contracts, optional choices that cannot be forced, extreme
resource boundaries, and unimplemented combinations of state transitions. Where
a permitted alternative such as HTTP/1.1 411 satisfies a case, the inventory does
not claim that the target's chunk parser was exercised.

## Current catalog after oracle retirements

The current catalog contains 749 cases: 266 HTTP/1.1 and 483 HTTP/2.
The earlier custom-scheme-retirement checkpoint contained 750 cases: 266 HTTP/1.1
and 484 HTTP/2.
The earlier CONNECT-retirement checkpoint contained 751 cases: 266 HTTP/1.1
and 485 HTTP/2.
`h2.server.connect-authority-form` was retired as an oracle correction (A4CR1):
an origin binding need not support CONNECT, and method-only application
publication cannot establish parsed authority. Its former positive shape row is
an explicit acceptance/authority-observation gap. Allowing 501 or a generic reset
would not establish parsing. The four malformed-CONNECT cases remain active and
unchanged; proxy tunnel establishment remains excluded.

`h2.server.request-non-http-scheme` was also retired as an oracle correction
(A4MS3). RFC9113 8.3.1 permits non-HTTP schemes for translating proxies and
gateways, while RFC9110 7.4 permits an origin to refuse an unconfigured target
URI. The fixture does not configure custom-scheme support or expose parsed
scheme. Its former positive row is an explicit acceptance/interpretation gap;
accepting 421 alone would not prove parsing.

`h2.server.reject-te-empty` was retired as an oracle correction (A4TE1).
RFC9110 10.1.4 and 5.6.1 permit a zero-member TE list; RFC9113 8.2.2
restricts request TE to trailers without explicitly resolving that boundary.
The inventory records an interpretation gap, with neither mandatory acceptance
nor rejection asserted. Non-trailers coding and response-TE rejection cases
remain active; historical empty-TE outcomes remain evidence.

These retirements are neither passing tests nor Ember fixes. The 752-case phase-2
catalog and its archived outcomes remain historical evidence; the
[historical failure entry](known-failures.md#h2-server-connect-authority-form)
records the retirement disposition and supporting evidence.


## Phase 3 checkpoint census

At the phase 3 checkpoint, the inventory had 1,796 rows: 915 covered, 729 explicit gaps and 152
scope exclusions, with 542 section rows. All 749 current case IDs (266 HTTP/1.1
and 483 HTTP/2) are mapped; referenced IDs and covered-role mappings reconcile.
These consistency checks do not establish normative completeness or the strength
of every claimed observation. Rows are not equally sized obligations, and these
counts are not a conformance percentage. The phase-2 snapshot above remains
historical; the three retired oracles remain explicit gaps.

The phase 3 failure registry contains 153 case IDs: 92 confirmed and 61
provisional, spanning 453 designated platform cells (150 JVM, 150 JS, 153 Native).
Registry skips are separate from inventory gaps and never count as passing tests.
See the independent checkpoint review (`audit/phase3-checkpoint-review.md`, historical artifact) and
sealed census evidence (`audit/phase3-checkpoint-review.zip`, historical artifact).

The phase 3 checkpoint (`audit/phase3-accepted-status.md`, historical artifact) records its completed
validation and limits. Phase 3 was committed as
`4dcb21681c71a013025ba4a4a140a63b4a781ee7`. The
phase 4 audit record (`audit/phase4-status.md`, historical artifact) tracks the subsequent independent
RFC-first audits, repairs and validation; the final phase 4 evidence record (`audit/phase4-accepted-status.md`, historical artifact) records execution and remaining acceptance gates.


## Phase 4 inventory census

The audited inventory contains 1,802 rows: 899 marked `covered`, 752 explicit
`gap` rows and 151 marked `excluded`. The last category mixes true global scope
exclusions with applicable obligations delegated to other inventory fragments;
it must not be read as 151 out-of-scope requirements. All 542 section headings
remain classified, and 22 errata dispositions are recorded. The executable
catalog remains 749 cases (266 HTTP/1.1 and 483 HTTP/2), with both roles covered.

The adversarial audits narrowed claims where an observation did not isolate the
named obligation. In particular, the two role-specific truncated HPACK size-update
cases also omit mandatory pseudoheaders: rejection alone does not prove an
HPACK-specific diagnosis. Unsupported oracles retired in phase 3 remain gaps.
The inventory records missing variants, state interactions and observation limits;
neither section classification nor these row counts establish comprehensive
executable coverage.

The phase 4 evidence record (`audit/phase4-accepted-status.md`, historical artifact) separates the completed
source reviews and full Scala/platform matrix from requirement coverage and gaps.
The immutable finding ledger (`audit/phase4-findings.md`, historical artifact) and
chronological evidence record (`audit/phase4-status.md`, historical artifact) retain prior findings,
repairs and failed validation attempts.
