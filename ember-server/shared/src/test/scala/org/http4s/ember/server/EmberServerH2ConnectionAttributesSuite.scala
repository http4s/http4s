/*
 * Copyright 2019 http4s.org
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

package org.http4s.ember.server

import cats.syntax.all._
import org.http4s._
import org.http4s.h2.H2Keys.Http2PriorKnowledge
import org.http4s.server.ServerRequestKeys

/** HTTP/2 requests over cleartext (prior knowledge) must carry the same connection
  * attributes as HTTP/1.1: ConnectionInfo with secure = false, and no SecureSession.
  *
  * The TLS cases are in [[EmberServerH2TLSConnectionAttributesSuite]].
  */
class EmberServerH2ConnectionAttributesSuite extends Http4sSuite with ConnectionAttributesHelpers {

  private def http2Requests =
    requestsSeenBy(identity, _.withHttp2, _.withAttribute(Http2PriorKnowledge, ()))

  private def http1Requests =
    requestsSeenBy(identity, identity, identity)

  test("HTTP/2 prior knowledge exposes ConnectionInfo with secure = false and no SecureSession") {
    http2Requests.map { case (server, reqs) =>
      val req = reqs.head
      assertEquals(req.httpVersion, HttpVersion.`HTTP/2`)
      assertLoopbackConnection(req, server)
      assertEquals(req.isSecure, Some(false))
      assert(!req.attributes.contains(ServerRequestKeys.SecureSession), "unexpected SecureSession")
      assertStreamAttributesKept(req)
    }
  }

  test("HTTP/2 prior knowledge matches HTTP/1.1 over cleartext") {
    (http2Requests, http1Requests).mapN { case ((_, h2), (_, h1)) =>
      assertEquals(h2.head.httpVersion, HttpVersion.`HTTP/2`)
      assertEquals(h1.head.httpVersion, HttpVersion.`HTTP/1.1`)
      assertEquals(h2.head.isSecure, h1.head.isSecure)
      assertEquals(
        h2.head.attributes.contains(ServerRequestKeys.SecureSession),
        h1.head.attributes.contains(ServerRequestKeys.SecureSession),
      )
    }
  }
}
