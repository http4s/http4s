/*
 * Copyright 2014 http4s.org
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

package org.http4s
package client
package middleware

import cats.effect.IO
import org.http4s.dsl.io._
import org.http4s.headers.{`Content-Encoding`, `Content-Length`}

class GZipSuite extends Http4sSuite {
  private val service = server.middleware.GZip(HttpApp[IO] {
    case GET -> Root / "gziptest" =>
      Ok("Dummy response")
    case HEAD -> Root / "gziptest" =>
      Ok()
  })
  private val gzipClient = GZip()(Client.fromHttpApp(service))

  test("Client Gzip should return data correctly") {
    gzipClient
      .get(Uri.unsafeFromString("/gziptest")) { response =>
        assert(response.status == Status.Ok)
        assert(response.headers.get[`Content-Encoding`].isEmpty)
        assert(response.headers.get[`Content-Length`].isEmpty)

        response.as[String]
      }
      .map { body =>
        assert(body == "Dummy response")
      }
  }

  test("Client Gzip should not decompress when the response body is empty") {
    val request = Request[IO](method = Method.HEAD, uri = Uri.unsafeFromString("/gziptest"))
    gzipClient
      .run(request)
      .use { response =>
        assert(response.status == Status.Ok)
        assert(response.headers.get[`Content-Encoding`].isEmpty)
        assert(response.headers.get[`Content-Length`].isEmpty)

        response.as[String]
      }
      .map { body =>
        assert(body == "")
      }
  }
}
