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
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.http4s.dsl.io._
import org.http4s.syntax.all._
import org.http4s.headers._
import java.util.Arrays
import org.scalacheck.effect.PropF
import scala.util.Properties

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

  // TODO: This test fails since fs2 and GZIPOutputStream disagree on the OS
  // byte in the gzip header
  // fs2: 1F 8B 08 00 00 00 00 00 00 00
  // gos: 1F 8B 08 00 00 00 00 00 00 FF
  // The last byte is the OS, 00 is FAT filesystem, while FF is unknown
  test("encodes random content-type if given isZippable is true") {
    assume(Properties.javaVersion != "16", "this test is skipped on JVM 16")

    val response = "Response string"
    val routes: HttpRoutes[IO] = HttpRoutes.of[IO] { case GET -> Root =>
      Ok(response, "Content-Type" -> "random-type; charset=utf-8")
    }

    val gzipRoutes: HttpRoutes[IO] = GZip(routes, isZippable = _ => true)

    val req: Request[IO] = Request[IO](Method.GET, uri"/")
      .putHeaders(`Accept-Encoding`(ContentCoding.gzip))
    val actual: IO[Array[Byte]] =
      gzipRoutes.orNotFound(req).flatMap(_.as[Chunk[Byte]]).map(_.toArray)

    val byteStream = new ByteArrayOutputStream(response.length)
    val gZIPStream = new GZIPOutputStream(byteStream)
    gZIPStream.write(response.getBytes)
    gZIPStream.close()

    actual.map(Arrays.equals(_, byteStream.toByteArray)).assert
  }

  // TODO: see above
  test("encoding") {
    assume(Properties.javaVersion != "16", "this test is skipped on JVM 16")

    PropF.forAllF { (vector: Vector[Array[Byte]]) =>
      val routes: HttpRoutes[IO] = HttpRoutes.of[IO] { case GET -> Root =>
        Ok(Stream.emits(vector).covary[IO])
      }
      val gzipRoutes: HttpRoutes[IO] = GZip(routes)
      val req: Request[IO] = Request[IO](Method.GET, uri"/")
        .putHeaders(`Accept-Encoding`(ContentCoding.gzip))
      val actual: IO[Array[Byte]] =
        gzipRoutes.orNotFound(req).flatMap(_.as[Chunk[Byte]]).map(_.toArray)

      val byteArrayStream = new ByteArrayOutputStream()
      val gzipStream = new GZIPOutputStream(byteArrayStream)
      vector.foreach(gzipStream.write)
      gzipStream.close()
      val expected = byteArrayStream.toByteArray

      actual.map(Arrays.equals(_, expected)).assert
    }
  }
}
