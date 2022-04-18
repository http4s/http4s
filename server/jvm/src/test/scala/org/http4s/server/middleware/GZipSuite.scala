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

import cats.effect._
import cats.implicits._
import fs2._
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.syntax.all._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalacheck.effect.PropF

import java.io.ByteArrayInputStream
import java.util.Arrays
import java.util.zip.GZIPInputStream

class GZipSuite extends Http4sSuite {
  test("fall through if the route doesn't match") {
    val routes = GZip(HttpRoutes.empty[IO]) <+> HttpRoutes.of[IO] { case GET -> Root =>
      Ok("pong")
    }
    val req =
      Request[IO](Method.GET, uri"/").putHeaders(`Accept-Encoding`(ContentCoding.gzip))
    routes
      .orNotFound(req)
      .map { resp =>
        resp.status === Status.Ok &&
        resp.headers.get[`Content-Encoding`].isEmpty
      }
      .assert
  }

  test("encodes random content-type if given isZippable is true") {
    val response = "Response string"
    val routes: HttpRoutes[IO] = HttpRoutes.of[IO] { case GET -> Root =>
      Ok(response, "Content-Type" -> "random-type; charset=utf-8")
    }

    val gzipRoutes: HttpRoutes[IO] = GZip(routes, isZippable = _ => true)

    val req: Request[IO] = Request[IO](Method.GET, uri"/")
      .putHeaders(`Accept-Encoding`(ContentCoding.gzip))
    val actual: IO[Array[Byte]] =
      gzipRoutes.orNotFound(req).flatMap(_.as[Chunk[Byte]]).map(_.toArray)

    actual.map { bytes =>
      val gzipStream = new GZIPInputStream(new ByteArrayInputStream(bytes))
      val decoded = new Array[Byte](response.length)
      gzipStream.read(decoded)
      Arrays.equals(response.getBytes(), decoded)
    }.assert
  }

  test("doesn't encode responses with HTTP status that doesn't allow entity") {
    val routes: HttpRoutes[IO] = HttpRoutes.of[IO] { case GET -> Root =>
      NoContent()
    }

    val gzipRoutes: HttpRoutes[IO] = GZip(routes)

    val req: Request[IO] = Request[IO](Method.GET, uri"/")
      .putHeaders(`Accept-Encoding`(ContentCoding.gzip))
    val resp: IO[Response[IO]] = gzipRoutes.orNotFound(req)

    resp.map(_.headers.get[`Content-Encoding`].isEmpty).assert
  }

  test("encoding") {
    val genByteArray =
      Gen.poisson(10).flatMap(n => Gen.buildableOfN[Array[Byte], Byte](n, arbitrary[Byte]))
    val genVector = Gen
      .poisson(10)
      .flatMap(n => Gen.buildableOfN[Vector[Array[Byte]], Array[Byte]](n, genByteArray))
    PropF.forAllF(genVector) { (vector: Vector[Array[Byte]]) =>
      val routes: HttpRoutes[IO] = HttpRoutes.of[IO] { case GET -> Root =>
        Ok(Stream.emits(vector))
      }
      val gzipRoutes: HttpRoutes[IO] = GZip(routes)
      val req: Request[IO] = Request[IO](Method.GET, uri"/")
        .putHeaders(`Accept-Encoding`(ContentCoding.gzip))
      val actual: IO[Array[Byte]] =
        gzipRoutes.orNotFound(req).flatMap(_.as[Chunk[Byte]]).map(_.toArray)

      actual.map { bytes =>
        val gzipStream = new GZIPInputStream(new ByteArrayInputStream(bytes))
        val decoded = new Array[Byte](vector.map(_.length).sum)
        gzipStream.read(decoded)
        Arrays.equals(Array.concat(vector: _*), decoded)
      }.assert
    }
  }
}
