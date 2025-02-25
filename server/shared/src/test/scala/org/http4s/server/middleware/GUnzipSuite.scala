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
package server
package middleware

import cats.effect.IO
import fs2._
import fs2.compression._
import fs2.io.compression._
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.syntax.all._
import org.typelevel.ci._

import java.util.Arrays

class GUnzipSuite extends Http4sSuite {

  test("decodes random content-type if content-encoding allows it") {
    val request = "Request string"
    val routes: HttpRoutes[IO] = HttpRoutes.of[IO] { case r @ POST -> Root => Ok(r.body) }
    val gzipRoutes: HttpRoutes[IO] = GUnzip(routes)

    val req: Request[IO] = Request[IO](Method.POST, uri"/")
      .putHeaders(Header.Raw(ci"Content-Encoding", "gzip"))
      .withBodyStream(Stream.emits(request.getBytes()).through(Compression[IO].gzip()))

    gzipRoutes.orNotFound(req).flatMap { response =>
      response.body.compile
        .to(Chunk)
        .map { decoded =>
          Arrays.equals(request.getBytes(), decoded.toArray)
        }
        .assert
    }
  }

  test("doesn't decode request if content-encoding doesn't allow it") {
    val request = "Request string"
    val routes: HttpRoutes[IO] = HttpRoutes.of[IO] { case r @ POST -> Root => Ok(r.body) }
    val gzipRoutes: HttpRoutes[IO] = GUnzip(routes)

    val req: Request[IO] = Request[IO](Method.POST, uri"/")
      .putHeaders(`Content-Encoding`(ContentCoding.identity))
      .withBodyStream(Stream.emits(request.getBytes()))

    gzipRoutes.orNotFound(req).flatMap { response =>
      response.body.compile
        .to(Chunk)
        .map { decoded =>
          Arrays.equals(request.getBytes(), decoded.toArray)
        }
        .assert
    }
  }

  test("returns response with MalformedMessageBodyFailure if request body isn't in gzip format") {
    val request = "Request string"
    val routes: HttpRoutes[IO] = HttpRoutes.of[IO] { case r @ POST -> Root => Ok(r.body) }
    val gzipRoutes: HttpRoutes[IO] = GUnzip(routes)

    val req: Request[IO] = Request[IO](Method.POST, uri"/")
      .putHeaders(Header.Raw(ci"Content-Encoding", "gzip"))
      .withBodyStream(Stream.emits(request.getBytes()))

    gzipRoutes.orNotFound(req).flatMap { response =>
      response.body.compile
        .to(Chunk)
        .intercept[MalformedMessageBodyFailure]
    }
  }

  test("safely decodes a gzip bomb") {
    val request = "Request string"
    val routes: HttpRoutes[IO] = HttpRoutes.of[IO] { case r @ POST -> Root => Ok(r.body) }
    val gzipRoutes: HttpRoutes[IO] = GUnzip(routes)

    val req: Request[IO] = Request[IO](Method.POST, uri"/")
      .putHeaders(Header.Raw(ci"Content-Encoding", "gzip"))
      .withBodyStream(
        Stream.emits(request.getBytes()).repeatN(1024 * 1024 * 1024).through(Compression[IO].gzip())
      )

    gzipRoutes.orNotFound(req).flatMap { response =>
      response.body
        .take(request.length.toLong)
        .compile
        .to(Chunk)
        .map { decoded =>
          Arrays.equals(request.getBytes(), decoded.toArray)
        }
        .assert
    }
  }
}
