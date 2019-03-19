package org.http4s

import cats.effect.IO
import java.io.File
import java.nio.file.Files
import org.http4s.Status.NotModified
import org.http4s.headers._
import org.specs2.matcher.MatchResult

class StaticFileSpec extends Http4sSpec {

  "StaticFile" should {
    "Determine the media-type based on the files extension" in {

      def check(f: File, tpe: Option[MediaType]): MatchResult[Any] = {
        val r = StaticFile.fromFile[IO](f, testBlockingExecutionContext).value.unsafeRunSync

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

      StaticFile.fromFile[IO](emptyFile, testBlockingExecutionContext).value must returnValue(
        beSome[Response[IO]])
    }

    "Don't send unmodified files" in {
      val emptyFile = File.createTempFile("empty", ".tmp")

      val request =
        Request[IO]().putHeaders(`If-Modified-Since`(HttpDate.MaxValue))
      val response = StaticFile
        .fromFile[IO](emptyFile, testBlockingExecutionContext, Some(request))
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
      val response = StaticFile
        .fromFile[IO](emptyFile, testBlockingExecutionContext, Some(request))
        .value
        .unsafeRunSync
      response must beSome[Response[IO]]
      response.map(_.status) must beSome(NotModified)
    }

    "Send partial file" in {
      def check(path: String): MatchResult[Any] = {
        val f = new File(path)
        val r =
          StaticFile
            .fromFile[IO](
              f,
              0,
              1,
              StaticFile.DefaultBufferSize,
              testBlockingExecutionContext,
              None,
              StaticFile.calcETag[IO])
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

      val fileSize = StaticFile.DefaultBufferSize * 2 + 10

      val gibberish = (for {
        i <- 0 until fileSize
      } yield i.toByte).toArray
      Files.write(emptyFile.toPath, gibberish)

      def check(file: File): MatchResult[Any] = {
        val r = StaticFile
          .fromFile[IO](
            file,
            0,
            fileSize.toLong - 1,
            StaticFile.DefaultBufferSize,
            testBlockingExecutionContext,
            None,
            StaticFile.calcETag[IO])
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

    "Read from a URL" in {
      val url = getClass.getResource("/lorem-ipsum.txt")
      val expected = scala.io.Source.fromURL(url, "utf-8").mkString
      val s = StaticFile
        .fromURL[IO](getClass.getResource("/lorem-ipsum.txt"), testBlockingExecutionContext)
        .value
        .unsafeRunSync
        .fold[EntityBody[IO]](sys.error("Couldn't find resource"))(_.body)
      // Expose problem with readInputStream recycling buffer.  chunks.compile.toVector
      // saves chunks, which are mutated by naive usage of readInputStream.
      // This ensures that we're making a defensive copy of the bytes for
      // things like CachingChunkWriter that buffer the chunks.
      new String(s.compile.to[Array].unsafeRunSync(), "utf-8") must_== expected
    }

    "Set content-length header from a URL" in {
      val url = getClass.getResource("/lorem-ipsum.txt")
      val len =
        StaticFile
          .fromURL[IO](url, testBlockingExecutionContext)
          .value
          .map(_.flatMap(_.contentLength))
      len must returnValue(beSome(24005L))
    }
  }
}
