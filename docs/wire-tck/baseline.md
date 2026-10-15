# Specification baseline

The TCK covers HTTP/1.1 message syntax and framing from
[RFC 9112](https://www.rfc-editor.org/rfc/rfc9112.html), HTTP/2 from
[RFC 9113](https://www.rfc-editor.org/rfc/rfc9113.html), and HPACK from
[RFC 7541](https://www.rfc-editor.org/rfc/rfc7541.html).
[RFC 9110](https://www.rfc-editor.org/rfc/rfc9110.html) supplies the HTTP semantics
needed to interpret those protocols.

The boundary is observable wire behavior. RFC 9110 requirements enter the scope
where they determine parsing, framing, fields, request targets, or protocol state.
For example, HEAD and CONNECT semantics affect framing, and URI syntax affects
request-target validation. This does not extend coverage to general method policy,
redirect handling, caching, or application retry behavior. TLS/ALPN, HTTP/3, proxy
forwarding and optional protocol extensions are also outside the baseline.

[SpecSections](../../wire-testkit/shared/src/main/scala/org/http4s/wire/SpecSections.scala)
classifies the RFC sections, while
[SpecInventory](../../wire-testkit/shared/src/main/scala/org/http4s/wire/SpecInventory.scala)
records the individual requirements, their executable cases, and explicit gaps
or exclusions. Those definitions determine the detailed scope; including an RFC
section does not imply that every requirement in it has a test.

[SpecErrata](../../wire-testkit/shared/src/main/scala/org/http4s/wire/SpecErrata.scala)
records which RFC corrections are incorporated and which are excluded or pending.
Only corrections explicitly marked as incorporated modify this baseline.
