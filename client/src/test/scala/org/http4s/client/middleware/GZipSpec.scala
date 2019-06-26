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
  })

  "Client Gzip" should {

    val gzipClient = GZip()(Client.fromHttpApp(server.middleware.GZip(service)))

    "return data correctly" in {

      val body = gzipClient.get(Uri.unsafeFromString("/gziptest")) { response =>
        response.status must_== Status.Ok
        response.headers
          .get(`Content-Encoding`)
          .map(_.contentCoding.coding)
          .getOrElse("") must_== "gzip"

        response.as[String]
      }

      body.unsafeRunSync() must_== "Dummy response"
    }
  }
}
