# Coverage inventory

Phase 1 establishes executable examples and independent oracle checks. This is a
scaffold, not a comprehensive coverage claim. Phase 2 adds a separately authored
requirements inventory, section classifications, grammar productions and explicit
gaps. Phase 4 independently challenges those classifications.

The phase-1 catalog covers valid and invalid HTTP/1 field/framing exchanges and
HTTP/2 PING/message-length exchanges in both roles. The complete list is
`SmokeCases.all`; it is also emitted in each suite manifest. All other applicable
requirements are presently coverage gaps.

The initial independently stated obligations and their case mappings are in
[inventory/phase1.tsv](inventory/phase1.tsv). These nine specific scenarios do not
classify their entire RFC sections as covered. Missing variants and every other
applicable obligation remain gaps until the section and obligation inventory is
expanded in phase 2.
