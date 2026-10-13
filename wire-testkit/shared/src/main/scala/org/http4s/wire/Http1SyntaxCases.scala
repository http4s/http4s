/*
 * Copyright 2026 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.wire

import cats.effect.IO
import cats.syntax.all._
import fs2.Chunk
import scodec.bits.ByteVector

/** Request lines, request targets, status lines, and fields derived from RFC 9112 Sections 2-5. */
object Http1SyntaxCases {
  val all: Vector[WireCase] = Vector(
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-2.2
    WireCase(
      "h1.server.leading-empty-line",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "2.2",
        "A server expecting a request-line should ignore at least one preceding empty line.",
        "SHOULD",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1, ServerScenario.Reply()).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("\r\nGET /leading HTTP/1.1\r\nHost: wire.test\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          received <- server.received
          _ <- Check.equal(received.path, "/leading")
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.1
    WireCase(
      "h1.server.method-case-sensitive",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "3.1",
        "Method tokens are case-sensitive; a valid extension method is not rewritten.",
        "syntax",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("get /method HTTP/1.1\r\nHost: wire.test\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          // A server can report an unimplemented method; successful dispatch must retain its token.
          _ <-
            if (response.status == 501) IO.unit
            else
              Check.equal(response.status, 200) *> server.received
                .flatMap(received => Check.equal(received.method, "get"))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.1
    WireCase(
      "h1.server.method-token-punctuation",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "3.1",
        "Method tokens are case-sensitive; a valid extension method is not rewritten.",
        "syntax",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("M!#$%&'*+-.^_`|~09 /method HTTP/1.1\r\nHost: wire.test\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          // A server can report an unimplemented method; successful dispatch must retain its token.
          _ <-
            if (response.status == 501) IO.unit
            else
              Check.equal(response.status, 200) *> server.received
                .flatMap(received => Check.equal(received.method, "M!#$%&'*+-.^_`|~09"))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-2.2
    WireCase(
      "h1.server.lowercase-http-name",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "2.2",
        "HTTP-name is case-sensitive; malformed messages should receive 400 and connection closure.",
        "SHOULD",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET / http/1.1\r\nHost: wire.test\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 400)
          _ <- Http1FramingCases.expectClosedAfterResponse(peer)
          observed <- server.pollReceived
          _ <- Check.equal(observed, None)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-2.2
    WireCase(
      "h1.server.two-digit-minor-version",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "2.2",
        "HTTP-version has a single minor digit; malformed messages should receive 400 and closure.",
        "SHOULD",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET / HTTP/1.12\r\nHost: wire.test\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 400)
          _ <- Http1FramingCases.expectClosedAfterResponse(peer)
          observed <- server.pollReceived
          _ <- Check.equal(observed, None)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-2.2
    WireCase(
      "h1.server.method-delimiter",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "2.2",
        "The method is a token; an embedded delimiter is outside the message grammar.",
        "SHOULD",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GE(T / HTTP/1.1\r\nHost: wire.test\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 400)
          _ <- Http1FramingCases.expectClosedAfterResponse(peer)
          observed <- server.pollReceived
          _ <- Check.equal(observed, None)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-2.2
    WireCase(
      "h1.server.field-name-delimiter",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "2.2",
        "Field names are tokens; malformed messages should receive 400 and connection closure.",
        "SHOULD",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET / HTTP/1.1\r\nHost: wire.test\r\nX(Wire: value\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 400)
          _ <- Http1FramingCases.expectClosedAfterResponse(peer)
          observed <- server.pollReceived
          _ <- Check.equal(observed, None)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2
    WireCase(
      "h1.server.space-in-request-target",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "3.2",
        "An invalid request-target should be rejected with 400 or redirected with an encoded target.",
        "SHOULD",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET /two words HTTP/1.1\r\nHost: wire.test\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check(
            response.status == 400 || response.status == 301,
            s"invalid request-target was not rejected or redirected: ${response.status}",
          )
          _ <-
            if (response.status == 301)
              Check(
                values(response.headers, "location") match {
                  case Vector(location) => encodedSpaceTarget(location)
                  case _ => false
                },
                "the redirect did not identify the properly encoded original target",
              )
            else IO.unit
          observed <- server.pollReceived
          _ <- Check.equal(observed, None)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2.1
    WireCase(
      "h1.server.origin-empty-query",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "3.2.1",
        "Origin-form consists of an absolute path and an optional query.",
        "syntax",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1, ServerScenario.Reply()).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET /empty? HTTP/1.1\r\nHost: wire.test\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          received <- server.received
          _ <- Check.equal(received.path, "/empty?")
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2.1
    WireCase(
      "h1.server.origin-double-slash",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "3.2.1",
        "Origin-form consists of an absolute path and an optional query.",
        "syntax",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1, ServerScenario.Reply()).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET //first/second HTTP/1.1\r\nHost: wire.test\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          received <- server.received
          _ <- Check.equal(received.path, "//first/second")
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2.1
    WireCase(
      "h1.server.origin-percent-encoded",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "3.2.1",
        "Origin-form consists of an absolute path and an optional query.",
        "syntax",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1, ServerScenario.Reply()).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET /a%20b/%2F?q=%23%3F HTTP/1.1\r\nHost: wire.test\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          received <- server.received
          _ <- Check.equal(normalizeEscapeCase(received.path), "/a%20b/%2F?q=%23%3F")
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2.1
    WireCase(
      "h1.server.origin-query-delimiters",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "3.2.1",
        "Origin-form consists of an absolute path and an optional query.",
        "syntax",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1, ServerScenario.Reply()).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET /query?first=/a?b&next=2 HTTP/1.1\r\nHost: wire.test\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          received <- server.received
          _ <- Check.equal(received.path, "/query?first=/a?b&next=2")
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2.2
    WireCase(
      "h1.server.absolute-form",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "3.2.2", "An origin server must accept absolute-form request targets.", "MUST"),
    ) { ctx =>
      ctx.server(Protocol.Http1, ServerScenario.Reply()).use { case (peer, server) =>
        for {
          _ <- peer
            .writeAscii("GET http://wire.test/absolute?q=1 HTTP/1.1\r\nHost: wire.test\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          received <- server.received
          _ <- Check.equal(received.method, "GET")
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2.2
    WireCase(
      "h1.server.absolute-form-empty-path",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "3.2.2", "An origin server must accept absolute-form request targets.", "MUST"),
    ) { ctx =>
      ctx.server(Protocol.Http1, ServerScenario.Reply()).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET http://wire.test HTTP/1.1\r\nHost: wire.test\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          received <- server.received
          _ <- Check.equal(received.method, "GET")
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2.2
    WireCase(
      "h1.server.absolute-form-conflicting-host",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "3.2.2", "An origin server must accept absolute-form request targets.", "MUST"),
    ) { ctx =>
      ctx.server(Protocol.Http1, ServerScenario.Reply()).use { case (peer, server) =>
        for {
          _ <- peer
            .writeAscii("GET http://wire.test/absolute?q=1 HTTP/1.1\r\nHost: other.test\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          received <- server.received
          _ <- Check.equal(received.method, "GET")
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2.4
    WireCase(
      "h1.server.asterisk-form",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "3.2.4",
        "A server-wide OPTIONS request uses the asterisk-form request-target.",
        "syntax",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1, ServerScenario.Reply()).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("OPTIONS * HTTP/1.1\r\nHost: wire.test\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          received <- server.received
          _ <- Check.equal(received.method, "OPTIONS")
          _ <- Check.equal(received.path, "*")
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2.3
    WireCase(
      "h1.server.authority-form",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "3.2.3",
        "CONNECT request-target syntax consists of a host and explicit port.",
        "syntax",
      ),
    ) { ctx =>
      // A 501 application response exercises request parsing without opening a tunnel.
      ctx.server(Protocol.Http1, ServerScenario.Reply(status = 501)).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("CONNECT wire.test:8080 HTTP/1.1\r\nHost: wire.test:8080\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer, requestMethod = "CONNECT")
          _ <- Check.equal(response.status, 501)
          received <- server.received
          _ <- Check.equal(received.method, "CONNECT")
          _ <- Check.equal(received.path, "wire.test:8080")
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2
    WireCase(
      "h1.server.host-case-insensitive",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "3.2",
        "A syntactically valid Host field accompanies the HTTP/1.1 request.",
        "syntax",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1, ServerScenario.Reply()).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET /host HTTP/1.1\r\nhOsT: wire.test\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          received <- server.received
          _ <- Check.equal(values(received.headers, "host"), Vector("wire.test"))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2
    WireCase(
      "h1.server.host-explicit-port",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "3.2",
        "A syntactically valid Host field accompanies the HTTP/1.1 request.",
        "syntax",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1, ServerScenario.Reply()).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET /host HTTP/1.1\r\nHost: wire.test:8080\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          received <- server.received
          _ <- Check.equal(values(received.headers, "host"), Vector("wire.test:8080"))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2
    WireCase(
      "h1.server.host-ipv6-literal",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "3.2",
        "A syntactically valid Host field accompanies the HTTP/1.1 request.",
        "syntax",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1, ServerScenario.Reply()).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET /host HTTP/1.1\r\nHost: [::1]:8080\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          received <- server.received
          _ <- Check.equal(values(received.headers, "host"), Vector("[::1]:8080"))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2
    WireCase(
      "h1.server.duplicate-host-identical",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "3.2",
        "Multiple Host field lines or an invalid Host field value require a 400 response.",
        "MUST",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET / HTTP/1.1\r\nHost: wire.test\r\nHost: wire.test\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 400)
          observed <- server.pollReceived
          _ <- Check.equal(observed, None)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2
    WireCase(
      "h1.server.duplicate-host-different",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "3.2",
        "Multiple Host field lines or an invalid Host field value require a 400 response.",
        "MUST",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET / HTTP/1.1\r\nHost: wire.test\r\nHost: other.test\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 400)
          observed <- server.pollReceived
          _ <- Check.equal(observed, None)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2
    WireCase(
      "h1.server.duplicate-host-case-insensitive",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "3.2",
        "Multiple Host field lines or an invalid Host field value require a 400 response.",
        "MUST",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET / HTTP/1.1\r\nHost: wire.test\r\nhOsT: wire.test\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 400)
          observed <- server.pollReceived
          _ <- Check.equal(observed, None)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2
    WireCase(
      "h1.server.host-nondigit-port",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "3.2",
        "Multiple Host field lines or an invalid Host field value require a 400 response.",
        "MUST",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET / HTTP/1.1\r\nHost: wire.test:abc\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 400)
          observed <- server.pollReceived
          _ <- Check.equal(observed, None)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2
    WireCase(
      "h1.server.host-userinfo",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "3.2",
        "Multiple Host field lines or an invalid Host field value require a 400 response.",
        "MUST",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET / HTTP/1.1\r\nHost: user@wire.test\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 400)
          observed <- server.pollReceived
          _ <- Check.equal(observed, None)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2
    WireCase(
      "h1.server.host-comma",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "3.2",
        "Embedded SP makes this comma-plus-space Host value invalid and requires a 400 response.",
        "MUST",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1).use { case (peer, server) =>
        for {
          // Comma alone is a legal reg-name sub-delimiter; the embedded SP is the defect.
          _ <- peer.writeAscii("GET / HTTP/1.1\r\nHost: wire.test, other.test\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 400)
          observed <- server.pollReceived
          _ <- Check.equal(observed, None)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2
    WireCase(
      "h1.server.host-path",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "3.2",
        "Multiple Host field lines or an invalid Host field value require a 400 response.",
        "MUST",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET / HTTP/1.1\r\nHost: wire.test/path\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 400)
          observed <- server.pollReceived
          _ <- Check.equal(observed, None)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2
    WireCase(
      "h1.server.host-space",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "3.2",
        "Multiple Host field lines or an invalid Host field value require a 400 response.",
        "MUST",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET / HTTP/1.1\r\nHost: wire test\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 400)
          observed <- server.pollReceived
          _ <- Check.equal(observed, None)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-5.1
    WireCase(
      "h1.server.field-name-tab-before-colon",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "5.1",
        "Whitespace between a request field name and colon requires a 400 response.",
        "MUST",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET / HTTP/1.1\r\nHost: wire.test\r\nX-Wire\t: value\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 400)
          observed <- server.pollReceived
          _ <- Check.equal(observed, None)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-5.1
    WireCase(
      "h1.server.field-no-ows",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "5.1",
        "Field extraction excludes boundary OWS and retains the field line value.",
        "syntax",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1, ServerScenario.Reply()).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET / HTTP/1.1\r\nHost: wire.test\r\nX-Wire:value\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          received <- server.received
          _ <- Check.equal(values(received.headers, "x-wire"), Vector("value"))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-5.1
    WireCase(
      "h1.server.field-boundary-ows",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "5.1",
        "Field extraction excludes boundary OWS and retains the field line value.",
        "syntax",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1, ServerScenario.Reply()).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET / HTTP/1.1\r\nHost: wire.test\r\nX-Wire:\t  value \t\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          received <- server.received
          _ <- Check.equal(values(received.headers, "x-wire"), Vector("value"))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-5.1
    WireCase(
      "h1.server.field-empty-value",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "5.1",
        "Field extraction excludes boundary OWS and retains the field line value.",
        "syntax",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1, ServerScenario.Reply()).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET / HTTP/1.1\r\nHost: wire.test\r\nX-Wire:\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          received <- server.received
          _ <- Check.equal(values(received.headers, "x-wire"), Vector(""))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-5.1
    WireCase(
      "h1.server.field-only-ows",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "5.1",
        "Field extraction excludes boundary OWS and retains the field line value.",
        "syntax",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1, ServerScenario.Reply()).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET / HTTP/1.1\r\nHost: wire.test\r\nX-Wire: \t \t\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          received <- server.received
          _ <- Check.equal(values(received.headers, "x-wire"), Vector(""))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-5.1
    WireCase(
      "h1.server.field-interior-whitespace",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "5.1",
        "Field extraction excludes boundary OWS and retains the field line value.",
        "syntax",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1, ServerScenario.Reply()).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET / HTTP/1.1\r\nHost: wire.test\r\nX-Wire: one\t two\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          received <- server.received
          _ <- Check.equal(values(received.headers, "x-wire"), Vector("one\t two"))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-5
    WireCase(
      "h1.server.field-name-token-punctuation",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "5", "Every tchar is permitted in a field name.", "syntax"),
    ) { ctx =>
      ctx.server(Protocol.Http1, ServerScenario.Reply()).use { case (peer, server) =>
        for {
          _ <- peer
            .writeAscii("GET / HTTP/1.1\r\nHost: wire.test\r\nX!#$%&'*+-.^_`|~09: token\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          received <- server.received
          _ <- Check.equal(values(received.headers, "X!#$%&'*+-.^_`|~09"), Vector("token"))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9110.html#section-5.5
    WireCase(
      "h1.server.field-obs-text-octets",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9110,
        "5.5",
        "Recipients should treat obs-text octets as opaque field content.",
        "SHOULD",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1).use { case (peer, server) =>
        for {
          // 80 FF C3 28 is legal obs-text/VCHAR but is not a valid UTF-8 sequence.
          _ <- peer.write(
            Http1Wire.ascii("GET /octets HTTP/1.1\r\nHost: wire.test\r\nX-Wire: ") ++
              ByteVector.fromValidHex("80ffc328") ++
              Http1Wire.ascii("\r\nX-After: intact\r\n\r\n")
          )
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          received <- server.received
          _ <- Check.equal(values(received.headers, "x-wire"), Vector("\u0080\u00ff\u00c3("))
          _ <- Check.equal(values(received.headers, "x-after"), Vector("intact"))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9110.html#section-5.3
    WireCase(
      "h1.server.repeated-field-order",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9110,
        "5.3",
        "Repeated list field values retain their received order whether separate or combined.",
        "syntax",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1, ServerScenario.Reply()).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii(
            "GET / HTTP/1.1\r\nHost: wire.test\r\nConnection: x-first\r\nConnection: x-second\r\n\r\n"
          )
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          received <- server.received
          _ <- Check.equal(
            commaTokens(values(received.headers, "connection")),
            Vector("x-first", "x-second"),
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-5.2
    WireCase(
      "h1.server.obsolete-fold-space",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "5.2",
        "A server must reject obsolete folding with 400 or replace each fold with spaces.",
        "MUST",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET / HTTP/1.1\r\nHost: wire.test\r\nX-Wire: one\r\n two\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <-
            if (response.status == 400)
              server.pollReceived.flatMap(observed => Check.equal(observed, None))
            else
              Check.equal(response.status, 200) *> server.received.flatMap(received =>
                Check(
                  normalizedFold(values(received.headers, "x-wire"), "one +two"),
                  "an accepted obsolete fold was not replaced with one or more spaces",
                )
              )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-5.2
    WireCase(
      "h1.server.obsolete-fold-tab",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "5.2",
        "A server must reject obsolete folding with 400 or replace each fold with spaces.",
        "MUST",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET / HTTP/1.1\r\nHost: wire.test\r\nX-Wire: one\r\n\ttwo\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <-
            if (response.status == 400)
              server.pollReceived.flatMap(observed => Check.equal(observed, None))
            else
              Check.equal(response.status, 200) *> server.received.flatMap(received =>
                Check(
                  normalizedFold(values(received.headers, "x-wire"), "one +two"),
                  "an accepted obsolete fold was not replaced with one or more spaces",
                )
              )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-2.2
    WireCase(
      "h1.server.whitespace-before-first-field",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "2.2",
        "Leading whitespace field lines must be rejected or completely ignored before interpretation.",
        "MUST",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii(
            "GET /leading-fields HTTP/1.1\r\n X-Ignored: first\r\n\tX-Also-Ignored: second\r\nHost: wire.test\r\nX-Keep: value\r\n\r\n"
          )
          status <- responseHeadOrEof(peer)
          _ <- status match {
            case Some(200) =>
              server.received.flatMap { received =>
                Check.equal(received.path, "/leading-fields") *>
                  Check.equal(values(received.headers, "x-ignored"), Vector.empty) *>
                  Check.equal(values(received.headers, "x-also-ignored"), Vector.empty) *>
                  Check.equal(values(received.headers, "x-keep"), Vector("value"))
              }
            case other =>
              Check(other.forall(_ >= 400), s"invalid whitespace was not rejected: $other") *>
                server.pollReceived.flatMap(observed => Check.equal(observed, None))
          }
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-2.2
    WireCase(
      "h1.server.field-bare-cr",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "2.2",
        "A field containing this control octet must be rejected or have the octet replaced with SP.",
        "MUST",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1).use { case (peer, server) =>
        for {
          _ <- peer.write(
            Http1Wire.ascii("GET / HTTP/1.1\r\nHost: wire.test\r\nX-Wire: one") ++
              ByteVector(13) ++ Http1Wire.ascii("two\r\n\r\n")
          )
          status <- responseHeadOrEof(peer)
          _ <- status match {
            case Some(200) =>
              server.received.flatMap(received =>
                Check.equal(values(received.headers, "x-wire"), Vector("one two"))
              )
            case other =>
              Check(other.forall(_ >= 400), s"invalid field was not rejected: $other") *>
                server.pollReceived.flatMap(observed => Check.equal(observed, None))
          }
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9110.html#section-5.5
    WireCase(
      "h1.server.field-nul",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9110,
        "5.5",
        "A field containing this control octet must be rejected or have the octet replaced with SP.",
        "MUST",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1).use { case (peer, server) =>
        for {
          _ <- peer.write(
            Http1Wire.ascii("GET / HTTP/1.1\r\nHost: wire.test\r\nX-Wire: one") ++
              ByteVector(0) ++ Http1Wire.ascii("two\r\n\r\n")
          )
          status <- responseHeadOrEof(peer)
          _ <- status match {
            case Some(200) =>
              server.received.flatMap(received =>
                Check.equal(values(received.headers, "x-wire"), Vector("one two"))
              )
            case other =>
              Check(other.forall(_ >= 400), s"invalid field was not rejected: $other") *>
                server.pollReceived.flatMap(observed => Check.equal(observed, None))
          }
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9110.html#section-4.1
    WireCase(
      "h1.server.uri-8000-octets",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9110,
        "4.1",
        "Senders and recipients should support URI lengths of at least 8000 octets.",
        "SHOULD",
      ),
    ) { ctx =>
      val target = "/" + ("a" * 7999)
      ctx.server(Protocol.Http1).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET " + target + " HTTP/1.1\r\nHost: wire.test\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          received <- server.received
          _ <- Check.equal(received.path, target)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-4
    WireCase(
      "h1.server.generated-status-line",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "4",
        "A generated status-line has a three-digit code and a space before its optional reason phrase.",
        "MUST",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1, ServerScenario.Reply(status = 299)).use { case (peer, _) =>
        for {
          _ <- peer.writeAscii("GET / HTTP/1.1\r\nHost: wire.test\r\n\r\n")
          line <- peer.readUntil(Http1Wire.crlf, 8192)
          rendered = latin1(line)
          _ <- Check(
            rendered.startsWith("HTTP/1.1 299 ") || rendered.startsWith("HTTP/1.0 299 "),
            s"invalid generated status-line: $rendered",
          )
          _ <- Check(
            rendered.drop(13).forall(c => c == '\t' || c >= ' ' && c != 127),
            "generated reason phrase contains an invalid control octet",
          )
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2.1
    WireCase(
      "h1.client.generated-origin-form",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "3.2.1",
        "A request sent directly to an origin uses only the path and query.",
        "MUST",
      ),
    ) { ctx =>
      ctx.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(path = "/sent?q=one%20two"))
          .use { response =>
            response.body.compile.drain *> Check.equal(response.status, 200)
          }
          .handleErrorWith(requiredAcceptanceFailure)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "GET")
          _ <- Check.equal(request.target, "/sent?q=one%20two")
          _ <- peer.writeAscii("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n")
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2.1
    WireCase(
      "h1.client.generated-empty-path",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "3.2.1",
        "A target URI with an empty path is sent with a slash request-target.",
        "MUST",
      ),
    ) { ctx =>
      ctx.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(path = ""))
          .use { response =>
            response.body.compile.drain *> Check.equal(response.status, 200)
          }
          .handleErrorWith(requiredAcceptanceFailure)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.target, "/")
          _ <- peer.writeAscii("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n")
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2.4
    WireCase(
      "h1.client.generated-asterisk-form",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "3.2.4",
        "A server-wide OPTIONS request uses only an asterisk as its request-target.",
        "MUST",
      ),
    ) { ctx =>
      ctx.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(method = "OPTIONS", path = "*"))
          .use { response =>
            response.body.compile.drain *> Check.equal(response.status, 200)
          }
          .handleErrorWith(requiredAcceptanceFailure)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "OPTIONS")
          _ <- Check.equal(request.target, "*")
          _ <- peer.writeAscii("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n")
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.1
    WireCase(
      "h1.client.generated-method-case",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "3.1",
        "The generated method token retains its exact case and valid token characters.",
        "syntax",
      ),
    ) { ctx =>
      ctx.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(method = "get"))
          .use { response =>
            response.body.compile.drain *> Check.equal(response.status, 200)
          }
          .handleErrorWith(requiredAcceptanceFailure)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "get")
          _ <- peer.writeAscii("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n")
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.1
    WireCase(
      "h1.client.generated-method-token",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "3.1",
        "The generated method token retains its exact case and valid token characters.",
        "syntax",
      ),
    ) { ctx =>
      ctx.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(method = "M!#$%&'*+-.^_`|~09"))
          .use { response =>
            response.body.compile.drain *> Check.equal(response.status, 200)
          }
          .handleErrorWith(requiredAcceptanceFailure)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.method, "M!#$%&'*+-.^_`|~09")
          _ <- peer.writeAscii("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n")
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2
    WireCase(
      "h1.client.generated-host",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "3.2",
        "Every HTTP/1.1 request must contain one Host field identifying its target authority.",
        "MUST",
      ),
    ) { ctx =>
      ctx.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData())
          .use { response =>
            response.body.compile.drain *> Check.equal(response.status, 200)
          }
          .handleErrorWith(requiredAcceptanceFailure)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          host = values(request.headers, "host")
          _ <- Check.equal(host.size, 1)
          _ <- Check(
            host.head.startsWith("127.0.0.1:") && host.head.drop(10).nonEmpty &&
              host.head.drop(10).forall(c => c >= '0' && c <= '9'),
            s"Host does not identify the loopback peer authority: $host",
          )
          _ <- peer.writeAscii("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n")
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-5
    WireCase(
      "h1.client.generated-field-token",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "5",
        "Generated fields use token names, a colon, and a single field line.",
        "syntax",
      ),
    ) { ctx =>
      ctx.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(headers = Vector("X!#$%&'*+-.^_`|~09" -> "one two")))
          .use { response =>
            response.body.compile.drain *> Check.equal(response.status, 200)
          }
          .handleErrorWith(requiredAcceptanceFailure)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(values(request.headers, "X!#$%&'*+-.^_`|~09"), Vector("one two"))
          _ <- peer.writeAscii("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n")
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9110.html#section-5.3
    WireCase(
      "h1.client.generated-repeated-field-order",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9110,
        "5.3",
        "A sender preserves ordered values of a list field when emitting repeated field lines.",
        "syntax",
      ),
    ) { ctx =>
      ctx.client(Protocol.Http1)(driver =>
        driver
          .exchange(
            RequestData(headers = Vector("Connection" -> "x-first", "Connection" -> "x-second"))
          )
          .use { response =>
            response.body.compile.drain *> Check.equal(response.status, 200)
          }
          .handleErrorWith(requiredAcceptanceFailure)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(
            commaTokens(values(request.headers, "connection")),
            Vector("x-first", "x-second"),
          )
          _ <- peer.writeAscii("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n")
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9110.html#section-4.1
    WireCase(
      "h1.client.generated-uri-8000-octets",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9110,
        "4.1",
        "Senders and recipients should support URI lengths of at least 8000 octets.",
        "SHOULD",
      ),
    ) { ctx =>
      val target = "/" + ("a" * 7999)
      ctx.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData(path = target))
          .use { response =>
            response.body.compile.drain *> Check.equal(response.status, 200)
          }
          .handleErrorWith(requiredAcceptanceFailure)
      ) { peer =>
        for {
          request <- Http1Wire.readRequest(peer)
          _ <- Check.equal(request.target, target)
          _ <- peer.writeAscii("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n")
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-4
    WireCase(
      "h1.client.empty-reason-phrase",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "4", "The reason phrase is optional after the required separating space.", "syntax"),
    ) { ctx =>
      ctx.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData())
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("ok"))

            } yield ()
          }
          .handleErrorWith(requiredAcceptanceFailure)
      )(peer =>
        Http1Wire.readRequest(peer) *> peer.writeAscii(
          "HTTP/1.1 200 \r\nContent-Length: 2\r\n\r\nok"
        )
      )
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-4
    WireCase(
      "h1.client.ignore-reason-phrase",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "4",
        "A client should ignore the reason phrase and interpret the numeric status.",
        "SHOULD",
      ),
    ) { ctx =>
      ctx.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData())
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("ok"))

            } yield ()
          }
          .handleErrorWith(requiredAcceptanceFailure)
      )(peer =>
        Http1Wire.readRequest(peer) *> peer.writeAscii(
          "HTTP/1.1 200 Not Found\r\nContent-Length: 2\r\n\r\nok"
        )
      )
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-4
    WireCase(
      "h1.client.reason-phrase-horizontal-tab",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "4", "A reason phrase may contain HTAB and SP.", "syntax"),
    ) { ctx =>
      ctx.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData())
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("ok"))

            } yield ()
          }
          .handleErrorWith(requiredAcceptanceFailure)
      )(peer =>
        Http1Wire.readRequest(peer) *> peer.writeAscii(
          "HTTP/1.1 200 one\t two\r\nContent-Length: 2\r\n\r\nok"
        )
      )
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-4
    WireCase(
      "h1.client.reason-phrase-obs-text",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "4", "The reason-phrase grammar permits opaque obs-text octets.", "syntax"),
    ) { ctx =>
      ctx.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData())
          .use { response =>
            response.body.compile.to(Chunk).flatMap { body =>
              Check.equal(response.status, 200) *> Check
                .equal(body.toByteVector, Http1Wire.ascii("ok"))
            }
          }
          .handleErrorWith(requiredAcceptanceFailure)
      )(peer =>
        Http1Wire.readRequest(peer) *> peer.write(
          Http1Wire.ascii("HTTP/1.1 200 ") ++ ByteVector.fromValidHex("80ff") ++
            Http1Wire.ascii("\r\nContent-Length: 2\r\n\r\nok")
        )
      )
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-4
    WireCase(
      "h1.client.unrecognized-status-code",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "4",
        "An unrecognized valid status code is accepted with its integer value and response body framing preserved.",
        "syntax",
      ),
    ) { ctx =>
      ctx.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData())
          .use { response =>
            response.body.compile.to(Chunk).flatMap { body =>
              Check.equal(response.status, 299) *> Check
                .equal(body.toByteVector, Http1Wire.ascii("ok"))
            }
          }
          .handleErrorWith(requiredAcceptanceFailure)
      )(peer =>
        Http1Wire.readRequest(peer) *> peer.writeAscii(
          "HTTP/1.1 299 Unknown Success\r\nContent-Length: 2\r\n\r\nok"
        )
      )
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-5.1
    WireCase(
      "h1.client.field-no-ows",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "5.1",
        "Field extraction excludes boundary OWS and retains the field line value.",
        "syntax",
      ),
    ) { ctx =>
      ctx.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData())
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("ok"))
              _ <- Check.equal(values(response.headers, "x-wire"), Vector("value"))
            } yield ()
          }
          .handleErrorWith(requiredAcceptanceFailure)
      )(peer =>
        Http1Wire.readRequest(peer) *> peer.writeAscii(
          "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nX-Wire:value\r\n\r\nok"
        )
      )
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-5.1
    WireCase(
      "h1.client.field-boundary-ows",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "5.1",
        "Field extraction excludes boundary OWS and retains the field line value.",
        "syntax",
      ),
    ) { ctx =>
      ctx.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData())
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("ok"))
              _ <- Check.equal(values(response.headers, "x-wire"), Vector("value"))
            } yield ()
          }
          .handleErrorWith(requiredAcceptanceFailure)
      )(peer =>
        Http1Wire.readRequest(peer) *> peer.writeAscii(
          "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nX-Wire:\t  value \t\r\n\r\nok"
        )
      )
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-5.1
    WireCase(
      "h1.client.field-empty-value",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "5.1",
        "Field extraction excludes boundary OWS and retains the field line value.",
        "syntax",
      ),
    ) { ctx =>
      ctx.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData())
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("ok"))
              _ <- Check.equal(values(response.headers, "x-wire"), Vector(""))
            } yield ()
          }
          .handleErrorWith(requiredAcceptanceFailure)
      )(peer =>
        Http1Wire.readRequest(peer) *> peer.writeAscii(
          "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nX-Wire:\r\n\r\nok"
        )
      )
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-5.1
    WireCase(
      "h1.client.field-only-ows",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "5.1",
        "Field extraction excludes boundary OWS and retains the field line value.",
        "syntax",
      ),
    ) { ctx =>
      ctx.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData())
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("ok"))
              _ <- Check.equal(values(response.headers, "x-wire"), Vector(""))
            } yield ()
          }
          .handleErrorWith(requiredAcceptanceFailure)
      )(peer =>
        Http1Wire.readRequest(peer) *> peer.writeAscii(
          "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nX-Wire: \t \t\r\n\r\nok"
        )
      )
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-5.1
    WireCase(
      "h1.client.field-interior-whitespace",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "5.1",
        "Field extraction excludes boundary OWS and retains the field line value.",
        "syntax",
      ),
    ) { ctx =>
      ctx.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData())
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("ok"))
              _ <- Check.equal(values(response.headers, "x-wire"), Vector("one\t two"))
            } yield ()
          }
          .handleErrorWith(requiredAcceptanceFailure)
      )(peer =>
        Http1Wire.readRequest(peer) *> peer.writeAscii(
          "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nX-Wire: one\t two\r\n\r\nok"
        )
      )
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-5
    WireCase(
      "h1.client.field-name-token-punctuation",
      Protocol.Http1,
      Role.Client,
      Rfc(9112, "5", "Every tchar is permitted in a field name.", "syntax"),
    ) { ctx =>
      ctx.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData())
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("ok"))
              _ <- Check.equal(values(response.headers, "X!#$%&'*+-.^_`|~09"), Vector("token"))
            } yield ()
          }
          .handleErrorWith(requiredAcceptanceFailure)
      )(peer =>
        Http1Wire.readRequest(peer) *> peer.writeAscii(
          "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nX!#$%&'*+-.^_`|~09: token\r\n\r\nok"
        )
      )
    },
    // https://www.rfc-editor.org/rfc/rfc9110.html#section-5.5
    WireCase(
      "h1.client.field-obs-text-octets",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9110,
        "5.5",
        "Recipients should treat obs-text octets as opaque field content.",
        "SHOULD",
      ),
    ) { ctx =>
      ctx.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData())
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("ok"))
              _ <- Check.equal(values(response.headers, "x-wire"), Vector("\u0080\u00ff\u00c3("))
              _ <- Check.equal(values(response.headers, "x-after"), Vector("intact"))
            } yield ()
          }
          .handleErrorWith(requiredAcceptanceFailure)
      )(peer =>
        Http1Wire.readRequest(peer) *> peer.write(
          Http1Wire.ascii("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nX-Wire: ") ++
            ByteVector.fromValidHex("80ffc328") ++
            Http1Wire.ascii("\r\nX-After: intact\r\n\r\nok")
        )
      )
    },
    // https://www.rfc-editor.org/rfc/rfc9110.html#section-5.3
    WireCase(
      "h1.client.repeated-field-order",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9110,
        "5.3",
        "Repeated list field values retain their received order whether separate or combined.",
        "syntax",
      ),
    ) { ctx =>
      ctx.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData())
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("ok"))
              _ <- Check.equal(
                commaTokens(values(response.headers, "connection")),
                Vector("x-first", "x-second"),
              )
            } yield ()
          }
          .handleErrorWith(requiredAcceptanceFailure)
      )(peer =>
        Http1Wire.readRequest(peer) *> peer.writeAscii(
          "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: x-first\r\nConnection: x-second\r\n\r\nok"
        )
      )
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-5.2
    WireCase(
      "h1.client.obsolete-fold-space",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "5.2",
        "A user agent must replace each obsolete response field fold with one or more spaces.",
        "MUST",
      ),
    ) { ctx =>
      ctx.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData())
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("ok"))
              _ <- Check(
                normalizedFold(values(response.headers, "x-wire"), "one +two"),
                "the obsolete fold was not replaced with one or more spaces",
              )
            } yield ()
          }
          .handleErrorWith(requiredAcceptanceFailure)
      )(peer =>
        Http1Wire.readRequest(peer) *> peer.writeAscii(
          "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nX-Wire: one\r\n two\r\n\r\nok"
        )
      )
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-5.2
    WireCase(
      "h1.client.obsolete-fold-tab",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "5.2",
        "A user agent must replace each obsolete response field fold with one or more spaces.",
        "MUST",
      ),
    ) { ctx =>
      ctx.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData())
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("ok"))
              _ <- Check(
                normalizedFold(values(response.headers, "x-wire"), "one +two"),
                "the obsolete fold was not replaced with one or more spaces",
              )
            } yield ()
          }
          .handleErrorWith(requiredAcceptanceFailure)
      )(peer =>
        Http1Wire.readRequest(peer) *> peer.writeAscii(
          "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nX-Wire: one\r\n\ttwo\r\n\r\nok"
        )
      )
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-5.2
    WireCase(
      "h1.client.obsolete-fold-multiple",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "5.2",
        "A user agent must replace each obsolete response field fold with one or more spaces.",
        "MUST",
      ),
    ) { ctx =>
      ctx.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData())
          .use { response =>
            for {
              body <- response.body.compile.to(Chunk)
              _ <- Check.equal(response.status, 200)
              _ <- Check.equal(body.toByteVector, Http1Wire.ascii("ok"))
              _ <- Check(
                normalizedFold(values(response.headers, "x-wire"), "one +two +three"),
                "the obsolete fold was not replaced with one or more spaces",
              )
            } yield ()
          }
          .handleErrorWith(requiredAcceptanceFailure)
      )(peer =>
        Http1Wire.readRequest(peer) *> peer.writeAscii(
          "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nX-Wire: one\r\n two\r\n\tthree\r\n\r\nok"
        )
      )
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-2.2
    WireCase(
      "h1.client.whitespace-before-first-field",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "2.2",
        "Leading whitespace field lines must be rejected or completely ignored before interpretation.",
        "MUST",
      ),
    ) { ctx =>
      ctx.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData())
          .use { response =>
            response.body.compile
              .to(Chunk)
              .map(body => (response.status, response.headers, body.toByteVector))
          }
          .attempt
          .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
          .flatMap {
            case Left(error) => rejected(error)
            case Right((status, headers, body)) =>
              Check.equal(status, 200) *>
                Check.equal(values(headers, "x-ignored"), Vector.empty) *>
                Check.equal(values(headers, "x-also-ignored"), Vector.empty) *>
                Check.equal(values(headers, "x-keep"), Vector("value")) *>
                Check.equal(body, Http1Wire.ascii("ok"))
          }
      )(peer =>
        Http1Wire.readRequest(peer) *> peer.writeAscii(
          "HTTP/1.1 200 OK\r\n X-Ignored: first\r\n\tX-Also-Ignored: second\r\nContent-Length: 2\r\nX-Keep: value\r\n\r\nok"
        )
      )
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-2.2
    WireCase(
      "h1.client.field-bare-cr",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9112,
        "2.2",
        "A field containing this control octet must be rejected or have the octet replaced with SP.",
        "MUST",
      ),
    ) { ctx =>
      ctx.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData())
          .use { response =>
            response.body.compile
              .to(Chunk)
              .map(body => (response.status, response.headers, body.toByteVector))
          }
          .attempt
          .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
          .flatMap {
            case Left(error) => rejected(error)
            case Right((status, headers, body)) =>
              Check.equal(status, 200) *>
                Check.equal(values(headers, "x-wire"), Vector("one two")) *>
                Check.equal(body, Http1Wire.ascii("ok"))
          }
      )(peer =>
        Http1Wire.readRequest(peer) *> peer.write(
          Http1Wire.ascii("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nX-Wire: one") ++
            ByteVector(13) ++ Http1Wire.ascii("two\r\n\r\nok")
        )
      )
    },
    // https://www.rfc-editor.org/rfc/rfc9110.html#section-5.5
    WireCase(
      "h1.client.field-nul",
      Protocol.Http1,
      Role.Client,
      Rfc(
        9110,
        "5.5",
        "A field containing this control octet must be rejected or have the octet replaced with SP.",
        "MUST",
      ),
    ) { ctx =>
      ctx.client(Protocol.Http1)(driver =>
        driver
          .exchange(RequestData())
          .use { response =>
            response.body.compile
              .to(Chunk)
              .map(body => (response.status, response.headers, body.toByteVector))
          }
          .attempt
          .flatTap(_.left.toOption.traverse_(RawPeer.raiseAdapterOrTimeout[IO]))
          .flatMap {
            case Left(error) => rejected(error)
            case Right((status, headers, body)) =>
              Check.equal(status, 200) *>
                Check.equal(values(headers, "x-wire"), Vector("one two")) *>
                Check.equal(body, Http1Wire.ascii("ok"))
          }
      )(peer =>
        Http1Wire.readRequest(peer) *> peer.write(
          Http1Wire.ascii("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nX-Wire: one") ++
            ByteVector(0) ++ Http1Wire.ascii("two\r\n\r\nok")
        )
      )
    },

    // https://www.rfc-editor.org/rfc/rfc9110.html#section-4.2.1
    WireCase(
      "h1.server.absolute-form-empty-host",
      Protocol.Http1,
      Role.Server,
      Rfc(9110, "4.2.1", "A recipient must reject an http URI with an empty host identifier."),
    ) { ctx =>
      ctx.server(Protocol.Http1).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET http:///empty-host HTTP/1.1\r\nHost: wire.test\r\n\r\n")
          status <- responseHeadOrEof(peer)
          _ <- Check(status.forall(_ >= 400), s"an empty http URI host was accepted: $status")
          observed <- server.pollReceived
          _ <- Check.equal(observed, None)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2
    WireCase(
      "h1.server.host-unclosed-ipv6-literal",
      Protocol.Http1,
      Role.Server,
      Rfc(9112, "3.2", "An invalid Host field value requires a 400 response."),
    ) { ctx =>
      ctx.server(Protocol.Http1).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET / HTTP/1.1\r\nHost: [::1\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 400)
          observed <- server.pollReceived
          _ <- Check.equal(observed, None)
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9110.html#section-7.2
    WireCase(
      "h1.server.host-empty-port",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9110,
        "7.2",
        "Host permits the URI port production to be empty after its colon.",
        "syntax",
      ),
    ) { ctx =>
      ctx.server(Protocol.Http1).use { case (peer, server) =>
        for {
          _ <- peer.writeAscii("GET / HTTP/1.1\r\nHost: wire.test:\r\n\r\n")
          response <- Http1Wire.readFinalResponse(peer)
          _ <- Check.equal(response.status, 200)
          received <- server.received
          _ <- Check.equal(values(received.headers, "host"), Vector("wire.test:"))
        } yield ()
      }
    },
    // https://www.rfc-editor.org/rfc/rfc9112.html#section-5
    WireCase(
      "h1.server.generated-field-token",
      Protocol.Http1,
      Role.Server,
      Rfc(
        9112,
        "5",
        "Generated fields use token names, a colon, and a single field line.",
        "syntax",
      ),
    ) { ctx =>
      ctx
        .server(
          Protocol.Http1,
          ServerScenario.Reply(headers = Vector("X!#$%&'*+-.^_`|~09" -> "one two")),
        )
        .use { case (peer, _) =>
          for {
            _ <- peer.writeAscii("GET / HTTP/1.1\r\nHost: wire.test\r\n\r\n")
            response <- Http1Wire.readFinalResponse(peer)
            _ <- Check.equal(response.status, 200)
            _ <- Check.equal(values(response.headers, "X!#$%&'*+-.^_`|~09"), Vector("one two"))
          } yield ()
        }
    },
  )

  private def values(headers: Vector[(String, String)], name: String): Vector[String] =
    headers.collect { case (field, value) if field.equalsIgnoreCase(name) => value }

  private def commaTokens(values: Vector[String]): Vector[String] =
    values.flatMap(_.split(",", -1)).map(_.trim)

  private def latin1(bytes: ByteVector): String =
    bytes.toArray.iterator.map(b => (b & 0xff).toChar).mkString

  private def tokenChar(c: Char): Boolean =
    c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' ||
      "!#$%&'*+-.^_`|~".contains(c)

  /** Observe either a complete response head or genuinely empty EOF. Partial heads are failures. */
  private def responseHeadOrEof(peer: RawPeer[IO], informational: Int = 0): IO[Option[Int]] =
    peer
      .readUntilOrClosed(Http1Wire.crlf ++ Http1Wire.crlf, 65536)
      .flatMap {
        case Some(bytes) =>
          val lines = latin1(bytes).split("\r\n", -1).toVector
          val line = lines.head
          val validStatus = line.length >= 13 &&
            (line.startsWith("HTTP/1.0 ") || line.startsWith("HTTP/1.1 ")) &&
            line.substring(9, 12).forall(c => c >= '0' && c <= '9') && line(12) == ' ' &&
            line.drop(13).forall(c => c == '\t' || c >= ' ' && c != 127)
          (for {
            _ <- Check(validStatus, s"malformed target status-line: $line")
            status = line.substring(9, 12).toInt
            _ <- Check(
              status >= 100 && status <= 599 && status != 101,
              s"unexpected target status: $status",
            )
            _ <- lines.tail.traverse_ { field =>
              val colon = field.indexOf(':')
              Check(
                colon > 0 && field.take(colon).forall(tokenChar) &&
                  field.drop(colon + 1).forall(c => c == '\t' || c >= ' ' && c != 127),
                s"malformed target field line: $field",
              )
            }
          } yield status).flatMap { status =>
            if (status >= 200) IO.pure(Some(status))
            else if (informational >= 16)
              IO.raiseError(new WireObservationLimit("informational response observation limit"))
            else IO.defer(responseHeadOrEof(peer, informational + 1))
          }
        case None => IO.pure(None)
      }

  private def normalizedFold(observed: Vector[String], logicalValue: String): Boolean =
    observed.nonEmpty && observed.forall(_.matches(logicalValue))

  // RFC 9110 4.2.3: escape hex digits are case-insensitive; reserved octets stay encoded.
  private def normalizeEscapeCase(value: String): String =
    "%[0-9a-fA-F]{2}".r.replaceAllIn(value, matched => matched.matched.map(_.toUpper))

  /** Equivalence for this fixture's http://wire.test/two%20words target (RFC 3986 2, 5 and 6.2;
    * RFC 9110 4.2.3 and 10.2.2). This does not implement general redirect policy.
    * Use an independent URI parser; keep empty path segments because they affect dot removal.
    */
  private def encodedSpaceTarget(location: String): Boolean = {
    // Empty HTTP ports equal the default port. Scala.js URI rejects the empty-port spelling.
    // Only normalize the reg-name authority; never remove a colon from a path or malformed port.
    val parseable = location.replaceFirst("(?i)^((?:http:)?//[^/?#:]*):([/?#]|$)", "$1$2")
    location.forall(_ < 128) && Either.catchNonFatal(new java.net.URI(parseable)).exists { uri =>
      def normalizeEscapes(value: String): String =
        "%([0-9a-fA-F]{2})".r.replaceAllIn(
          value,
          matched => {
            val c = Integer.parseInt(matched.group(1), 16).toChar
            if (
              c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' ||
              c >= '0' && c <= '9' || "-._~".contains(c)
            ) c.toString
            else matched.matched.map(_.toUpper)
          },
        )

      val scheme = Option(uri.getScheme).getOrElse("http")
      val authority = normalizeEscapes(Option(uri.getRawAuthority).getOrElse("wire.test"))
      // The original path is at the authority root, so relative paths merge beneath '/'.
      val rawPath = Option(uri.getRawPath).getOrElse("")
      val path = normalizeEscapes(rawPath)
      val segments = path.stripPrefix("/").split("/", -1).toVector
      val resolved = segments.zipWithIndex.foldLeft(Vector.empty[String]) {
        case (acc, (segment, index)) =>
          val trailing = if (index == segments.size - 1) Vector("") else Vector.empty
          segment match {
            case "." => acc ++ trailing
            case ".." => acc.dropRight(1) ++ trailing
            case other => acc :+ other
          }
      }
      !uri.isOpaque && rawPath.nonEmpty &&
      (uri.getScheme == null && !location.startsWith("//") || uri.getRawAuthority != null) &&
      scheme.equalsIgnoreCase("http") &&
      authority.matches("(?i)wire\\.test(?::0*80|:)?") &&
      uri.getRawQuery == null && uri.getRawFragment == null &&
      resolved == Vector("two%20words")
    }
  }

  private def requiredAcceptanceFailure(error: Throwable): IO[Unit] =
    RawPeer.raiseAdapterOrTimeout[IO](error) *> (error match {
      case limit: WireObservationLimit => IO.raiseError(limit)
      case failure: AssertionError => IO.raiseError(failure)
      case _ => Check(false, s"the target rejected a response requiring acceptance: $error")
    })

  private def rejected(error: Throwable): IO[Unit] =
    RawPeer.raiseAdapterOrTimeout[IO](error) *> (error match {
      case limit: WireObservationLimit => IO.raiseError(limit)
      case assertion: AssertionError => IO.raiseError(assertion)
      case _ => IO.unit
    })

}
