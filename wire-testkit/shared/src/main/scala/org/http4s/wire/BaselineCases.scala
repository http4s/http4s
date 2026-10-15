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

object BaselineCases {
  lazy val all: Vector[WireCase] = Vector(
    h1ServerMissingHost
  )

  // https://www.rfc-editor.org/rfc/rfc9112.html#section-3.2
  // A server MUST respond 400 to any HTTP/1.1 request lacking Host.
  lazy val h1ServerMissingHost: WireCase = WireCase(
    "h1.server.missing-host",
    Protocol.Http1,
    Role.Server,
    Rfc(9112, "3.2", "Reject an HTTP/1.1 request with no Host field using 400."),
  ) { context =>
    context.server(Protocol.Http1).use { case (peer, _) =>
      for {
        _ <- peer.writeAscii("GET / HTTP/1.1\r\n\r\n")
        response <- Http1Wire.readFinalResponse(peer)
        _ <- Check.equal(response.status, 400)
      } yield ()
    }
  }

}
