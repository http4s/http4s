/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
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
      val response = gzipClient.run(request).use[IO, String] { response =>
        response.status must_== Status.Ok
        response.headers.get(`Content-Encoding`) must beSome(`Content-Encoding`(ContentCoding.gzip))

        response.as[String]
      }

      response.unsafeRunSync() must_== ""
    }
  }
}
