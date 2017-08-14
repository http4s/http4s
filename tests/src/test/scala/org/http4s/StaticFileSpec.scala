package org.http4s

import fs2.Chunk
import fs2.interop.cats._
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.concurrent.ExecutorService

import org.http4s.Status.NotModified
import org.http4s.headers.{`Content-Length`, `Content-Type`, `If-Modified-Since`, `Last-Modified`}
import org.http4s.util.ByteVectorChunk
import org.specs2.matcher.MatchResult
import scodec.bits.ByteVector

class StaticFileSpec extends Http4sSpec {

  "StaticFile" should {
    "Determine the media-type based on the files extension" in {

      def check(f: File, tpe: Option[MediaType]): MatchResult[Any] = {
        val r = StaticFile.fromFile(f).value.unsafeRun

        r must beSome[Response]
        r.flatMap(_.headers.get(`Content-Type`)) must_== tpe.map(t => `Content-Type`(t))
        // Other headers must be present
        r.flatMap(_.headers.get(`Last-Modified`)).isDefined must beTrue
        r.flatMap(_.headers.get(`Content-Length`)).isDefined must beTrue
        r.flatMap(_.headers.get(`Content-Length`).map(_.length)) must beSome(f.length())
      }

      val tests = Seq("/Animated_PNG_example_bouncing_beach_ball.png" -> Some(MediaType.`image/png`),
                      "/test.fiddlefaddle" -> None)
      forall(tests) { case (p, om) =>
        check(new File(getClass.getResource(p).toURI), om)
      }
    }

    "handle an empty file" in {
      val emptyFile = File.createTempFile("empty", ".tmp")

      StaticFile.fromFile(emptyFile).value must returnValue(beSome[Response])
    }

    "Don't send unmodified files" in {
      val emptyFile = File.createTempFile("empty", ".tmp")

      val request = Request().putHeaders(`If-Modified-Since`(Instant.MAX))
      val response = StaticFile.fromFile(emptyFile, Some(request)).value.unsafeRun
      response must beSome[Response]
      response.map(_.status) must beSome(NotModified)
    }

    "Send partial file" in {
      def check(path: String): MatchResult[Any] = {
        val f = new File(path)
        val r = StaticFile.fromFile(f, 0, 1, StaticFile.DefaultBufferSize, None).value.unsafeRun

        r must beSome[Response]
        // Length is only 1 byte
        r.flatMap(_.headers.get(`Content-Length`).map(_.length)) must beSome(1)
        // get the Body to check the actual size
        r.map(_.body.runLog.unsafeRun.length) must beSome(1L)
      }

      val tests = List("./testing/src/test/resources/logback-test.xml",
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
        val r = StaticFile.fromFile(file, 0, fileSize.toLong - 1, StaticFile.DefaultBufferSize, None).value.unsafeRun

        r must beSome[Response]
        // Length of the body must match
        r.flatMap(_.headers.get(`Content-Length`).map(_.length)) must beSome(fileSize - 1)
        // get the Body to check the actual size
        val body = r.map(_.body.runLog.unsafeRun)
        body.map(_.length) must beSome(fileSize - 1)
        // Verify the context
        body.map(bytes => java.util.Arrays.equals(bytes.toArray, java.util.Arrays.copyOfRange(gibberish, 0, fileSize - 1))) must beSome(true)
      }

      check(emptyFile)
    }

    "Read from a URL" in {
      val url = getClass.getResource("/lorem-ipsum.txt")
      val expected = scala.io.Source.fromURL(url, "utf-8").mkString
      val s = StaticFile.fromURL(url)
        .value.unsafeRun
        .fold[EntityBody](sys.error("Couldn't find resource"))(_.body)
      // Expose problem with readInputStream recycling buffer.  chunks.runLog
      // saves chunks, which are mutated by naive usage of readInputStream.
      // This ensures that we're making a defensive copy of the bytes for
      // things like CachingChunkWriter that buffer the chunks.
      new String(Chunk.concat(s.chunks.runLog.unsafeRun).toArray, "utf-8") must_== expected
    }

    "Set content-length header from a URL" in {
      val url = getClass.getResource("/lorem-ipsum.txt")
      val len = StaticFile.fromURL(url)
        .value.map(_.flatMap(_.contentLength))
      len must returnValue(Some(24005L))
    }
  }
}
