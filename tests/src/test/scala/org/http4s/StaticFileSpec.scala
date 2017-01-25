package org.http4s

import java.io.File
import java.util.concurrent.ExecutorService

import org.http4s.Http4sSpec.TestPool
import org.http4s.headers.`Content-Type`

class StaticFileSpec extends Http4sSpec {

  "StaticFile" should {
    "Determine the media-type based on the files extension" in {

      def check(path: String, tpe: Option[MediaType]) = {
        val f = new File(path)
        val r = StaticFile.fromFile(f)

        r must beSome[Response]
        r.flatMap(_.headers.get(`Content-Type`)) must_== tpe.map(t => `Content-Type`(t))
      }

      val tests = Seq("./testing/src/test/resources/logback-test.xml"-> Some(MediaType.`text/xml`),
                      "./server/src/test/resources/testresource.txt" -> Some(MediaType.`text/plain`),
                      ".travis.yml" -> None)

      forall(tests){ case (p,om) => check(p, om) }
    }

    "handle an empty file" in {
      val emptyFile = File.createTempFile("empty", null)

      StaticFile.fromFile(emptyFile) must beSome[Response]
    }

  }
}
