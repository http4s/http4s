/*
 * Copyright 2013 http4s.org
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

import cats.effect.IO
import cats.syntax.all._
import java.io.File
import java.net.URL
import java.nio.file.Files

import org.http4s.Status._
import org.http4s.headers.ETag.EntityTag
import org.http4s.headers._
import cats.data.Nested
import java.net.UnknownHostException

class StaticFileSuite extends Http4sSuite {
  test("Determine the media-type based on the files extension") {
    def check(f: File, tpe: Option[MediaType]): IO[Boolean] =
      StaticFile.fromFile[IO](f, testBlocker).value.map { r =>
        r.isDefined &&
        r.flatMap(_.headers.get(`Content-Type`)) == tpe.map(t => `Content-Type`(t)) &&
        // Other headers must be present
        r.flatMap(_.headers.get(`Last-Modified`)).isDefined &&
        r.flatMap(_.headers.get(`Content-Length`)).isDefined &&
        r.flatMap(_.headers.get(`Content-Length`).map(_.length)) === Some(f.length())
      }

    val tests = List(
      "/Animated_PNG_example_bouncing_beach_ball.png" -> Some(MediaType.image.png),
      "/test.fiddlefaddle" -> None)
    tests.traverse { case (p, om) =>
      check(new File(getClass.getResource(p).toURI), om)
    }
  }
  test("load from resource") {
    def check(resource: String, status: Status): IO[Unit] = {
      val res1 = StaticFile
        .fromResource[IO](resource, testBlocker)
        .value

      Nested(res1)
        .map(_.status)
        .value
        .map(_.getOrElse(NotFound))
        .assertEquals(status)
    }

    val tests = List(
      "/Animated_PNG_example_bouncing_beach_ball.png" -> Ok,
      "/ball.png" -> Ok,
      "ball.png" -> Ok,
      "Animated_PNG_example_bouncing_beach_ball.png" -> Ok,
      "/test.fiddlefaddle" -> Ok,
      "test.fiddlefaddle" -> Ok,
      "//test.fiddlefaddle" -> Ok,
      "missing.html" -> NotFound,
      "/missing.html" -> NotFound
    )

    tests.traverse(Function.tupled(check))
  }

  test("load from resource using different classloader") {
    val loader = new ClassLoader() {
      override def getResource(name: String): URL =
        getClass.getClassLoader.getResource(name)
    }

    def check(resource: String, status: Status): IO[Unit] = {
      val res1 = StaticFile
        .fromResource[IO](resource, testBlocker, classloader = Some(loader))
        .value

      Nested(res1).map(_.status).value.map(_.getOrElse(NotFound)).assertEquals(status)
    }

    val tests = List(
      "/Animated_PNG_example_bouncing_beach_ball.png" -> Ok,
      "/ball.png" -> Ok,
      "ball.png" -> Ok,
      "Animated_PNG_example_bouncing_beach_ball.png" -> Ok,
      "/test.fiddlefaddle" -> Ok,
      "test.fiddlefaddle" -> Ok,
      "missing.html" -> NotFound,
      "/missing.html" -> NotFound
    )

    tests.traverse(Function.tupled(check))
  }

  test("handle an empty file") {
    val emptyFile = File.createTempFile("empty", ".tmp")

    StaticFile.fromFile[IO](emptyFile, testBlocker).value.map(_.isDefined).assert
  }

  test("Don't send unmodified files") {
    val emptyFile = File.createTempFile("empty", ".tmp")

    val request =
      Request[IO]().putHeaders(`If-Modified-Since`(HttpDate.MaxValue))
    val response = StaticFile
      .fromFile[IO](emptyFile, testBlocker, Some(request))
      .value
    Nested(response).map(_.status).value.assertEquals(Some(NotModified))
  }

  test("Don't send unmodified files by ETag") {
    val emptyFile = File.createTempFile("empty", ".tmp")

    val request =
      Request[IO]().putHeaders(
        `If-None-Match`(
          EntityTag(s"${emptyFile.lastModified().toHexString}-${emptyFile.length().toHexString}")))
    val response = StaticFile
      .fromFile[IO](emptyFile, testBlocker, Some(request))
      .value
    Nested(response).map(_.status).value.assertEquals(Some(NotModified))
  }

  test("Don't send unmodified files when both ETag and last modified date match") {
    val emptyFile = File.createTempFile("empty", ".tmp")

    val request =
      Request[IO]().putHeaders(
        `If-Modified-Since`(HttpDate.MaxValue),
        `If-None-Match`(
          EntityTag(s"${emptyFile.lastModified().toHexString}-${emptyFile.length().toHexString}")))

    val response = StaticFile
      .fromFile[IO](emptyFile, testBlocker, Some(request))
      .value
    Nested(response).map(_.status).value.assertEquals(Some(NotModified))
  }

  test("Send file when last modified date matches but etag does not match") {
    val emptyFile = File.createTempFile("empty", ".tmp")

    val request =
      Request[IO]()
        .putHeaders(`If-Modified-Since`(HttpDate.MaxValue), `If-None-Match`(EntityTag(s"12345")))

    val response = StaticFile
      .fromFile[IO](emptyFile, testBlocker, Some(request))
      .value
    Nested(response).map(_.status).value.assertEquals(Some(Ok))
  }

  test("Send file when etag matches, but last modified does not match") {
    val emptyFile = File.createTempFile("empty", ".tmp")

    val request =
      Request[IO]()
        .putHeaders(
          `If-Modified-Since`(HttpDate.MinValue),
          `If-None-Match`(
            EntityTag(
              s"${emptyFile.lastModified().toHexString}-${emptyFile.length().toHexString}")))

    val response = StaticFile
      .fromFile[IO](emptyFile, testBlocker, Some(request))
      .value
    Nested(response).map(_.status).value.assertEquals(Some(Ok))
  }

  test("Send partial file") {
    def check(path: String): IO[Unit] =
      IO(new File(path)).flatMap { f =>
        StaticFile
          .fromFile[IO](
            f,
            0,
            1,
            StaticFile.DefaultBufferSize,
            testBlocker,
            None,
            StaticFile.calcETag[IO])
          .value
          .flatMap { r =>
            // Length is only 1 byte
            assertEquals(r.flatMap(_.headers.get(`Content-Length`).map(_.length)), Some(1L))
            // get the Body to check the actual size
            r.map(_.body.compile.toVector.map(_.length)).traverse(_.assertEquals(1))
          }
          .void
      }

    val tests = List(
      "./testing/src/test/resources/logback-test.xml",
      "./server/src/test/resources/testresource.txt")

    tests.traverse(check)
  }

  test("Send file larger than BufferSize") {
    val emptyFile = File.createTempFile("some", ".tmp")
    emptyFile.deleteOnExit()

    val fileSize = StaticFile.DefaultBufferSize * 2 + 10

    val gibberish = (for {
      i <- 0 until fileSize
    } yield i.toByte).toArray
    Files.write(emptyFile.toPath, gibberish)

    def check(file: File): IO[Unit] =
      StaticFile
        .fromFile[IO](
          file,
          0,
          fileSize.toLong - 1,
          StaticFile.DefaultBufferSize,
          testBlocker,
          None,
          StaticFile.calcETag[IO])
        .value
        .flatMap { r =>
          // Length of the body must match
          assertEquals(
            r.flatMap(_.headers.get(`Content-Length`).map(_.length)),
            Some(fileSize.toLong - 1L))
          // get the Body to check the actual size
          r.map(_.body.compile.toVector)
            .map { body =>
              body.map(_.length).assertEquals(fileSize - 1) *>
                // Verify the context
                body
                  .map(bytes =>
                    java.util.Arrays
                      .equals(
                        bytes.toArray,
                        java.util.Arrays.copyOfRange(gibberish, 0, fileSize - 1)))
                  .assert
            }
            .getOrElse(IO.raiseError(new RuntimeException("test error")))
        }

    check(emptyFile)
  }

  test("Read from a URL") {
    val url = getClass.getResource("/lorem-ipsum.txt")
    val expected = scala.io.Source.fromURL(url, "utf-8").mkString
    val s = StaticFile
      .fromURL[IO](getClass.getResource("/lorem-ipsum.txt"), testBlocker)
      .value
      .map(_.fold[EntityBody[IO]](sys.error("Couldn't find resource"))(_.body))
    // Expose problem with readInputStream recycling buffer.  chunks.compile.toVector
    // saves chunks, which are mutated by naive usage of readInputStream.
    // This ensures that we're making a defensive copy of the bytes for
    // things like CachingChunkWriter that buffer the chunks.
    s.flatMap(_.compile.to(Array).map(new String(_, "utf-8"))).assertEquals(expected)
  }

  test("Set content-length header from a URL") {
    val url = getClass.getResource("/lorem-ipsum.txt")
    val len =
      StaticFile
        .fromURL[IO](url, testBlocker)
        .value
        .map(_.flatMap(_.contentLength))
    len.assertEquals(Some(24005L))
  }

  test("return none from a URL that is a directory") {
    // val url = getClass.getResource("/foo")
    StaticFile
      .fromURL[IO](getClass.getResource("/foo"), testBlocker)
      .value
      .assertEquals(None)
  }

  test("return none from a URL that points to a resource that does not exist") {
    StaticFile
      .fromURL[IO](new URL("https://github.com/http4s/http4s/fooz"), testBlocker)
      .value
      .assertEquals(None)
  }

  test("raise exception when url does not exist") {
    StaticFile
      .fromURL[IO](new URL("https://quuzgithubfoo.com/http4s/http4s/fooz"), testBlocker)
      .value
      .intercept[UnknownHostException]
  }
}
