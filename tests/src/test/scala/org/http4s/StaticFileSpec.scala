package org.http4s

import java.io.File
import java.time.Instant
import java.util.concurrent.ExecutorService

import org.http4s.Http4sSpec.TestPool
import org.http4s.Status.NotModified
import org.http4s.headers.{`Content-Length`, `Content-Type`, `If-Modified-Since`, `Last-Modified`}
import org.specs2.matcher.MatchResult

class StaticFileSpec extends Http4sSpec {

  "StaticFile" should {
    "Determine the media-type based on the files extension" in {

      def check(path: String, tpe: Option[MediaType]): MatchResult[Any] = {
        val f = new File(path)
        val r = StaticFile.fromFile(f)

        r must beSome[Response]
        r.flatMap(_.headers.get(`Content-Type`)) must_== tpe.map(t => `Content-Type`(t))
        // Other headers must be present
        r.flatMap(_.headers.get(`Last-Modified`)).isDefined must beTrue
        r.flatMap(_.headers.get(`Content-Length`)).isDefined must beTrue
        r.flatMap(_.headers.get(`Content-Length`).map(_.length)) must beSome(f.length())
      }

      val tests = Seq("./testing/src/test/resources/logback-test.xml"-> Some(MediaType.`text/xml`),
                      "./server/src/test/resources/testresource.txt" -> Some(MediaType.`text/plain`),
                      ".travis.yml" -> None)

      forall(tests){ case (p, om) => check(p, om) }
    }

    "handle an empty file" in {
      val emptyFile = File.createTempFile("empty", ".tmp")

      StaticFile.fromFile(emptyFile) must beSome[Response]
    }

    "Don't send unmodified files" in {
      val emptyFile = File.createTempFile("empty", ".tmp")

      val request = Request().putHeaders(`If-Modified-Since`(Instant.MAX))
      val response = StaticFile.fromFile(emptyFile, Some(request))
      response must beSome[Response]
      response.map(_.status) must beSome(NotModified)
    }

  }
}
