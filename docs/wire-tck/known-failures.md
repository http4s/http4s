# Known Ember protocol failures

Tracking records correspond to exact entries in `IgnoreRegistry.scala`. Provisional
observations are not confirmed implementation defects. Unexpected fixture or oracle
failures block acceptance and must not be hidden here.

## h1-server-missing-host

Case: `h1.server.missing-host`. Platforms: JVM, JS, Native. Review: provisional.
[RFC 9112 §3.2](https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2)
requires 400 when an HTTP/1.1 request has no Host. With `GET / HTTP/1.1\r\n\r\n`,
Ember returns 200 and the scenario body `accepted`. All three platforms reproduced
this in the phase-1 full comparison on Scala 2.13.18. The minimal Netty codec
pipeline also returns 200; that agreement does not override the RFC. Evidence:
`target/wire-tck-reports/2.13.18/comparison/<platform>/CompareEmberHttp1ServerSuite/h1.server.missing-host.{json,wire.txt}`.

Catalog evidence revision: `7ae5b1ea14c6df3f9b6b00ba5a74b2ff7adcde2d:94a49f23066fca48842cef98008d3d09439b013a3727b068dbeef9c07f68d50b`.
No Ember production fix is included.


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
