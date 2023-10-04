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
import fs2.io.compression._
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Encoding`
import org.http4s.headers.`Content-Length`
import org.http4s.syntax.literals._

class GZipSuite extends Http4sSuite {
  private val service = server.middleware.GZip(HttpApp[IO] {
    case GET -> Root / "gziptest" =>
      Ok("Dummy response")
    case HEAD -> Root / "gziptest" =>
      Ok()
    case _ => NotFound()
  })
  private val gzipClient = GZip()(Client.fromHttpApp(service))

  test("Client Gzip should return data correctly") {
    gzipClient
      .get(uri"/gziptest") { response =>
        assertEquals(response.status, Status.Ok)
        assertEquals(response.headers.get[`Content-Encoding`], None)
        assertEquals(response.headers.get[`Content-Length`], None)

        response.as[String]
      }
      .map { body =>
        assertEquals(body, "Dummy response")
      }
  }

  test("Client Gzip should not decompress when the response body is empty") {
    val request = Request[IO](method = Method.HEAD, uri = uri"/gziptest")
    gzipClient
      .run(request)
      .use { response =>
        assertEquals(response.status, Status.Ok)
        assertEquals(response.headers.get[`Content-Encoding`], None)
        assertEquals(response.headers.get[`Content-Length`], None)

        response.as[String]
      }
      .map { body =>
        assertEquals(body, "")
      }
  }
}
