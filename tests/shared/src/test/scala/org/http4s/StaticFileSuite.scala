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

import cats.data.Nested
import cats.effect.IO
import cats.syntax.all._
import fs2.Chunk
import fs2.io.file.Files
import fs2.io.file.Path
import org.http4s.Status._
import org.http4s.headers._
import org.http4s.testing.AutoCloseableResource

import java.net.URL
import java.net.UnknownHostException

class StaticFileSuite extends Http4sSuite {
  test("Determine the media-type based on the files extension") {
    def check(p: Path, tpe: Option[MediaType]): IO[Boolean] =
      (StaticFile.fromPath[IO](p).value, Files[IO].getBasicFileAttributes(p)).mapN { (r, attr) =>
        r.isDefined &&
        r.flatMap(_.headers.get[`Content-Type`]) == tpe.map(t => `Content-Type`(t)) &&
        // Other headers must be present
        r.flatMap(_.headers.get[`Last-Modified`]).isDefined &&
        r.flatMap(_.headers.get[`Content-Length`]).isDefined &&
        r.flatMap(_.headers.get[`Content-Length`].map(_.length)) === Some(attr.size)
      }

    val tests = List(
      "/Animated_PNG_example_bouncing_beach_ball.png" -> Some(MediaType.image.png),
      "/test.fiddlefaddle" -> None,
    )
    tests.parTraverse { case (p, om) =>
      check(CrossPlatformResource(p), om)
    }
  }

  if (Platform.isJvm)
    test("verify custom etag") {
      val etagCalculator: URL => IO[Option[ETag]] = _ => Some(ETag("42")).pure[IO]
      val resp = StaticFile
        .fromResource[IO](
          "/Animated_PNG_example_bouncing_beach_ball.png",
          None,
          true,
          None,
          etagCalculator = etagCalculator,
        )
        .value
      val headers = Nested(resp).map(_.headers)
      val etagHeader = headers.map(_.get[ETag]).value.map(_.flatten)
      etagHeader.assertEquals(ETag(s"42").some)
    }

  if (Platform.isJvm)
    test("verify etag on known resource") {
      val resp = StaticFile.fromResource[IO]("/Animated_PNG_example_bouncing_beach_ball.png").value
      val headers = Nested(resp).map(_.headers)
      val etagHeader = headers.map(_.get[ETag]).value.map(_.flatten)
      etagHeader.assertEquals(ETag(s"182aeb4e0bd-10015").some)
    }

  if (Platform.isJvm)
    test("verify etag absent on unknown resource") {
      val resp = StaticFile.fromResource[IO]("/unknown_resource.png").value
      val etagHeader = Nested(resp).map(_.headers.get[ETag]).value.map(_.flatten)

      etagHeader.assertEquals(None)
    }

  if (Platform.isJvm)
    test("load from resource") {
      def check(resource: String, status: Status): IO[Unit] = {
        val res1 = StaticFile
          .fromResource[IO](resource)
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
        "/missing.html" -> NotFound,
      )

      tests.parTraverse(Function.tupled(check))
    }

  if (Platform.isJvm)
    test("load from resource using different classloader") {
      val loader = new ClassLoader() {
        override def getResource(name: String): URL =
          getClass.getClassLoader.getResource(name)
      }

      def check(resource: String, status: Status): IO[Unit] = {
        val res1 = StaticFile
          .fromResource[IO](resource, classloader = Some(loader))
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
        "/missing.html" -> NotFound,
      )

      tests.parTraverse(Function.tupled(check))
    }

  test("handle an empty file") {
    val emptyFile = Files[IO].createTempFile(None, "empty", ".tmp", None)

    emptyFile.flatMap(StaticFile.fromPath[IO](_).value).map(_.isDefined).assert
  }

  test("handle a symlink") {
    StaticFile
      .fromPath[IO](Path("tests/shared/src/test/resources/lipsum-symlink.txt"))
      .semiflatMap(_.body.compile.count)
      .getOrElse(0L)
      .map2(Files[IO].size(CrossPlatformResource("/lorem-ipsum.txt")))(assertEquals(_, _))
  }

  test("Don't send unmodified files") {
    val emptyFile = Files[IO].createTempFile(None, "empty", ".tmp", None)

    val request =
      Request[IO]().putHeaders(`If-Modified-Since`(HttpDate.MaxValue))
    val response = emptyFile.flatMap(
      StaticFile
        .fromPath[IO](_, Some(request))
        .value
    )
    Nested(response).map(_.status).value.assertEquals(Some(NotModified))
  }

  test("Don't send unmodified files by ETag") {
    Files[IO].createTempFile(None, "empty", ".tmp", None).flatMap { emptyFile =>
      Files[IO]
        .getBasicFileAttributes(emptyFile)
        .flatMap { attr =>
          val request =
            Request[IO]().putHeaders(
              `If-None-Match`(
                EntityTag(s"${attr.lastModifiedTime.toMillis.toHexString}-${attr.size.toHexString}")
              )
            )
          val response = StaticFile
            .fromPath[IO](emptyFile, Some(request))
            .value
          Nested(response).map(_.status).value
        }
        .assertEquals(Some(NotModified))
    }
  }

  test("Don't send unmodified files when both ETag and last modified date match") {
    Files[IO]
      .createTempFile(None, "empty", ".tmp", None)
      .flatMap { emptyFile =>
        Files[IO]
          .getBasicFileAttributes(emptyFile)
          .flatMap { attr =>
            val request =
              Request[IO]().putHeaders(
                `If-Modified-Since`(HttpDate.MaxValue),
                `If-None-Match`(
                  EntityTag(
                    s"${attr.lastModifiedTime.toMillis.toHexString}-${attr.size.toHexString}"
                  )
                ),
              )

            val response = StaticFile
              .fromPath[IO](emptyFile, Some(request))
              .value

            Nested(response).map(_.status).value
          }
      }
      .assertEquals(Some(NotModified))
  }

  test("Send file when last modified date matches but etag does not match") {
    val emptyFile = Files[IO].createTempFile(None, "empty", ".tmp", None)

    val request =
      Request[IO]()
        .putHeaders(`If-Modified-Since`(HttpDate.MaxValue), `If-None-Match`(EntityTag(s"12345")))

    val response = emptyFile.flatMap(
      StaticFile
        .fromPath[IO](_, Some(request))
        .value
    )
    Nested(response).map(_.status).value.assertEquals(Some(Ok))
  }

  test("Send file when etag matches, but last modified does not match") {
    Files[IO]
      .createTempFile(None, "empty", ".tmp", None)
      .flatMap { emptyFile =>
        Files[IO].getBasicFileAttributes(emptyFile).flatMap { attr =>
          val request =
            Request[IO]()
              .putHeaders(
                `If-Modified-Since`(HttpDate.MinValue),
                `If-None-Match`(
                  EntityTag(
                    s"${attr.lastModifiedTime.toMillis.toHexString}-${attr.size.toHexString}"
                  )
                ),
              )

          val response = StaticFile
            .fromPath[IO](emptyFile, Some(request))
            .value
          Nested(response).map(_.status).value

        }
      }
      .assertEquals(Some(Ok))
  }

  test("Send partial file") {
    def check(path: String): IO[Unit] =
      StaticFile
        .fromPath[IO](
          Path(path),
          0,
          1,
          StaticFile.DefaultBufferSize,
          None,
          StaticFile.calculateETag[IO],
        )
        .value
        .flatMap { r =>
          // Length is only 1 byte
          assertEquals(r.flatMap(_.headers.get[`Content-Length`].map(_.length)), Some(1L))
          // get the Body to check the actual size
          r.map(_.body.compile.toVector.map(_.length)).traverse(_.assertEquals(1))
        }
        .void

    val tests = List(
      "./tests/shared/src/main/resources/logback-test.xml",
      "./server/shared/src/test/resources/testresource.txt",
    )

    tests.parTraverse(check)
  }

  test("Send file larger than BufferSize") {
    Files[IO].tempFile(None, "some", ".tmp", None).use { emptyFile =>
      val fileSize = StaticFile.DefaultBufferSize * 2 + 10

      val gibberish = (for {
        i <- 0 until fileSize
      } yield i.toByte).toArray
      val write = fs2.Stream
        .chunk(Chunk.array(gibberish))
        .through(Files[IO].writeAll(emptyFile))
        .compile
        .drain

      def check(path: Path): IO[Unit] =
        StaticFile
          .fromPath[IO](
            path,
            start = 0,
            end = fileSize.toLong - 1,
            buffsize = StaticFile.DefaultBufferSize,
            None,
            StaticFile.calculateETag[IO],
          )
          .value
          .flatMap { r =>
            // Length of the body must match
            assertEquals(
              r.flatMap(_.headers.get[`Content-Length`].map(_.length)),
              Some(fileSize.toLong - 1L),
            )
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
                          java.util.Arrays.copyOfRange(gibberish, 0, fileSize - 1),
                        )
                    )
                    .assert
              }
              .getOrElse(IO.raiseError(new RuntimeException("test error")))
          }

      write *> check(emptyFile)
    }
  }

  if (Platform.isJvm)
    test("Read from a URL") {
      val url = getClass.getResource("/lorem-ipsum.txt")
      val expected =
        AutoCloseableResource.resource(scala.io.Source.fromURL(url, "utf-8"))(_.mkString)
      val s = StaticFile
        .fromURL[IO](getClass.getResource("/lorem-ipsum.txt"))
        .value
        .map(_.fold[EntityBody[IO]](sys.error("Couldn't find resource"))(_.body))
      // Expose problem with readInputStream recycling buffer.  chunks.compile.toVector
      // saves chunks, which are mutated by naive usage of readInputStream.
      // This ensures that we're making a defensive copy of the bytes for
      // things like CachingChunkWriter that buffer the chunks.
      s.flatMap(_.compile.to(Array).map(new String(_, "utf-8"))).assertEquals(expected)
    }

  if (Platform.isJvm)
    test("Set content-length header from a URL") {
      val url = getClass.getResource("/lorem-ipsum.txt")
      val len =
        StaticFile
          .fromURL[IO](url)
          .value
          .map(_.flatMap(_.contentLength))
      len.assertEquals(Some(24005L))
    }

  if (Platform.isJvm)
    test("return none from a file URL that is a directory") {
      // val url = getClass.getResource("/foo")
      StaticFile
        .fromURL[IO](getClass.getResource("/foo"))
        .value
        .assertEquals(None)
    }

  if (Platform.isJvm)
    test("not return none from an HTTP URL whose path is a directory") {
      // We need a universal directory that also exists as a resource on
      // a server.  Creating a temp directory would be better, but then
      // we need an HTTP server that responds to a wildcard path.
      //
      // Or we can be lazy and just use `/`.
      Files[IO]
        .getBasicFileAttributes(Path("/"))
        .flatMap { attr =>
          assume(attr.isDirectory, "/ is not a directory")
          StaticFile
            .fromURL[IO](new URL("https://github.com//"))
            .value
            .map(_.fold(Status.NotFound)(_.status))
        }
        .assertEquals(Status.Ok)
    }

  if (Platform.isJvm)
    test("return none from a URL that points to a resource that does not exist") {
      StaticFile
        .fromURL[IO](new URL("https://github.com/http4s/http4s/fooz"))
        .value
        .assertEquals(None)
    }

  if (Platform.isJvm)
    test("raise exception when url does not exist") {
      StaticFile
        .fromURL[IO](new URL("https://quuzgithubfoo.com/http4s/http4s/fooz"))
        .value
        .intercept[UnknownHostException]
    }
}
