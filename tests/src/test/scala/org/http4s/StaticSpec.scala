package org.http4s

import cats.effect.{IO, Sync}
import java.io.File
import java.nio.file.Files
import org.http4s.Status.NotModified
import org.http4s.headers._
import org.specs2.matcher.MatchResult


class StaticSpec extends Http4sSpec {

  val static = Static(Sync[IO], contextShift)

  "Static" should {
    "Determine the media-type based on the files extension" in {

      def check(f: File, tpe: Option[MediaType]): MatchResult[Any] = {
        val r = static.fromFile(f = f, blockingExecutionContext = testBlockingExecutionContext).value.unsafeRunSync

        r must beSome[Response[IO]]
        r.flatMap(_.headers.get(`Content-Type`)) must_== tpe.map(t => `Content-Type`(t))
        // Other headers must be present
        r.flatMap(_.headers.get(`Last-Modified`)).isDefined must beTrue
        r.flatMap(_.headers.get(`Content-Length`)).isDefined must beTrue
        r.flatMap(_.headers.get(`Content-Length`).map(_.length)) must beSome(f.length())
      }

      val tests = Seq(
        "/Animated_PNG_example_bouncing_beach_ball.png" -> Some(MediaType.image.png),
        "/test.fiddlefaddle" -> None)
      forall(tests) {
        case (p, om) =>
          check(new File(getClass.getResource(p).toURI), om)
      }
    }

    "handle an empty file" in {
      val emptyFile = File.createTempFile("empty", ".tmp")

      static.fromFile(f = emptyFile, blockingExecutionContext = testBlockingExecutionContext).value must returnValue(
        beSome[Response[IO]])
    }

    "Don't send unmodified files" in {
      val emptyFile = File.createTempFile("empty", ".tmp")

      val request =
        Request[IO]().putHeaders(`If-Modified-Since`(HttpDate.MaxValue))
      val response = static
        .fromFile(f = emptyFile, blockingExecutionContext = testBlockingExecutionContext, req = Some(request))
        .value
        .unsafeRunSync
      response must beSome[Response[IO]]
      response.map(_.status) must beSome(NotModified)
    }

    "Don't send unmodified files by ETag" in {
      val emptyFile = File.createTempFile("empty", ".tmp")

      val request =
        Request[IO]().putHeaders(
          ETag(s"${emptyFile.lastModified().toHexString}-${emptyFile.length().toHexString}"))
      val response = static
        .fromFile(f = emptyFile, blockingExecutionContext = testBlockingExecutionContext, req = Some(request))
        .value
        .unsafeRunSync
      response must beSome[Response[IO]]
      response.map(_.status) must beSome(NotModified)
    }

    "Send partial file" in {
      def check(path: String): MatchResult[Any] = {
        val f = new File(path)
        val r =
          static
            .fromFile(
              f = f,
              blockingExecutionContext = testBlockingExecutionContext,
              start = 0L,
              end = Some(1L),
              buffsize = Static.DefaultBufferSize,
              req = None)
            .value
            .unsafeRunSync

        r must beSome[Response[IO]]
        // Length is only 1 byte
        r.flatMap(_.headers.get(`Content-Length`).map(_.length)) must beSome(1)
        // get the Body to check the actual size
        r.map(_.body.compile.toVector.unsafeRunSync.length) must beSome(1)
      }

      val tests = List(
        "./testing/src/test/resources/logback-test.xml",
        "./server/src/test/resources/testresource.txt",
        ".travis.yml")

      forall(tests)(check)
    }

    "Send file larger than BufferSize" in {
      val emptyFile = File.createTempFile("some", ".tmp")
      emptyFile.deleteOnExit()

      val fileSize = Static.DefaultBufferSize * 2 + 10

      val gibberish = (for {
        i <- 0 until fileSize
      } yield i.toByte).toArray
      Files.write(emptyFile.toPath, gibberish)

      def check(file: File): MatchResult[Any] = {
        val r = static
          .fromFile(
            f = file,
            blockingExecutionContext = testBlockingExecutionContext,
            start = 0L,
            end = Some(fileSize.toLong - 1),
            buffsize = Static.DefaultBufferSize,
            req = None)
          .value
          .unsafeRunSync

        r must beSome[Response[IO]]
        // Length of the body must match
        r.flatMap(_.headers.get(`Content-Length`).map(_.length)) must beSome(fileSize - 1)
        // get the Body to check the actual size
        val body = r.map(_.body.compile.toVector.unsafeRunSync)
        body.map(_.length) must beSome(fileSize - 1)
        // Verify the context
        body.map(
          bytes =>
            java.util.Arrays.equals(
              bytes.toArray,
              java.util.Arrays.copyOfRange(gibberish, 0, fileSize - 1))) must beSome(true)
      }

      check(emptyFile)
    }
  }

}