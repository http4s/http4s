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
import org.http4s.headers.`Content-Encoding`

class GZipSpec extends Http4sSpec {
  val service = server.middleware.GZip(HttpApp[IO] {
    case GET -> Root / "gziptest" =>
      Ok("Dummy response")
    case HEAD -> Root / "gziptest" =>
      Ok()
  })

  "Client Gzip" should {
    val gzipClient = GZip()(Client.fromHttpApp(service))

    "return data correctly" in {
      val body = gzipClient.get(Uri.unsafeFromString("/gziptest")) { response =>
        response.status must_== Status.Ok
        response.headers.get(`Content-Encoding`) must beSome(`Content-Encoding`(ContentCoding.gzip))

        response.as[String]
      }

      body.unsafeRunSync() must_== "Dummy response"
    }

    "not decompress when the response body is empty" in {
      val request = Request[IO](method = Method.HEAD, uri = Uri.unsafeFromString("/gziptest"))
      val response = gzipClient.run(request).use[String] { response =>
        response.status must_== Status.Ok
        response.headers.get(`Content-Encoding`) must beSome(`Content-Encoding`(ContentCoding.gzip))

        response.as[String]
      }

      response.unsafeRunSync() must_== ""
    }
  }
}
