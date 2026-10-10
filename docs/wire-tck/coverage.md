# Coverage inventory

The inventory and executable catalog answer different questions. Inventory rows
classify obligations, alternatives, grammar productions and exceptions derived
from the RFCs. Catalog manifests identify the concrete scenarios that actually
ran. A covered inventory row does not mean every possible input was tested, and
a passing scenario does not establish conformance to its entire RFC section.

The [section index](sections.tsv) classifies every heading in the four baseline
RFCs, independently of test discovery. The detailed [inventory fragments](inventory/)
separate client/server obligations and label each row `covered`, `gap`, or
`excluded`. `covered` requires specific executable case IDs; `gap` identifies
missing variants or observations; `excluded` explains the scope boundary.
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

The authored catalog contains 752 individually registered cases: 266 HTTP/1.1
and 486 HTTP/2, including HPACK and cross-section interactions. The independent
section index classifies 542 headings. Its 1,780 detailed inventory rows currently
include 933 mapped to cases, 695 explicit coverage gaps, and 152 scope exclusions.
These rows are not equally sized obligations and are not a conformance percentage.
No applicable section remains an unexpanded section placeholder; this is an
indexing check, subject to the independent phase-4 specification audit.

The recorded gaps include sender-side grammar variants, observations unavailable
through the target contracts, optional choices that cannot be forced, extreme
resource boundaries, and unimplemented combinations of state transitions. Where
a permitted alternative such as HTTP/1.1 411 satisfies a case, the inventory does
not claim that the target's chunk parser was exercised.
