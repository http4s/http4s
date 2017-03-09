package org.http4s

import java.io.File
import headers.`Content-Type`


class StaticFileSpec extends Http4sSpec {

  "StaticFile" should {
    "Determine the media-type based on the files extension" in {

      def check(f: File, tpe: Option[MediaType]) = {
        val r = StaticFile.fromFile(f)

        r must beSome[Response]
        r.flatMap(_.headers.get(`Content-Type`)) must_== tpe.map(t => `Content-Type`(t))
      }

      val tests = Seq("/Animated_PNG_example_bouncing_beach_ball.png" -> Some(MediaType.`image/png`),
                      "/test.fiddlefaddle" -> None)
      forall(tests) { case (p, om) =>
        check(new File(getClass.getResource(p).toURI), om)
      }
    }

    "handle an empty file" in {
      val emptyFile = File.createTempFile("empty", null)

      StaticFile.fromFile(emptyFile) must beSome[Response]
    }

  }
}
