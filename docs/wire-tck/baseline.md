# Specification baseline

Frozen on 2026-10-10. The selected editions are RFC 9112, RFC 9113, RFC 7541, and explicitly listed RFC 9110 dependencies. Later updates and optional extension RFCs are outside this baseline. TLS/ALPN, HTTP/3, proxy forwarding, caching and general application semantics are excluded. Scala.js uses Node.js only as its execution runtime; there is no Node comparison adapter.

| RFC | Authoritative text | SHA-256 of TXT |
|---|---|---|
| 9112 | [RFC 9112](https://www.rfc-editor.org/rfc/rfc9112.html) | `e4f426bac6206b67fdf9e0da826154f70588db2133a0a86b15cde4ff725d8937` |
| 9113 | [RFC 9113](https://www.rfc-editor.org/rfc/rfc9113.html) | `a00ef91b64e111a282e77ec66980f5242e77c0bb5e33e0927e3b6757080506de` |
| 7541 | [RFC 7541](https://www.rfc-editor.org/rfc/rfc7541.html) | `2239d7f8fb839b69ae2e928e685559b11376888269f131512197a0e3bacf7f7a` |
| 9110 | [RFC 9110](https://www.rfc-editor.org/rfc/rfc9110.html) | `21c1cdce6ab0e5509b04d84a28000836c7a087cf786efe6f04877ebfff47232a` |

Verified errata incorporated: RFC 9112 [7744](https://www.rfc-editor.org/errata/eid7744) (obs-text reference to 9110 §5.5) and [8284](https://www.rfc-editor.org/errata/eid8284) (editorial change-log placement); RFC 9113 [7013](https://www.rfc-editor.org/errata/eid7013) (DATA terminology). RFC 9110 verified errata 7105, 7109, 7138, 7306, 7419 and 8268 are listed by the RFC Editor inline-errata index; section-by-section applicability is pending. RFC 7541 errata lookup is pending (the RFC Editor search endpoint returned errors). Final audit acceptance requires completing both classifications.

RFC 9110 dependencies: §§2.2–2.5 conformance/error handling; §4.1 URI grammar and §4.2.1 http scheme; §§5.1–5.6.4 field names, occurrence/order, values, lists, whitespace, tokens and quoted strings; §§6.3–6.5 message body/trailer boundaries; §7.2 Host and §7.6.1 Connection; §8.6 Content-Length; §9.3.2 HEAD and §9.3.6 CONNECT only insofar as they determine framing. Other semantics are excluded unless an inventory row explicitly brings in a framing dependency.
