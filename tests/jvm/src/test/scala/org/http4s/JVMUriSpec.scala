package org.http4s

import java.nio.file.Paths
import org.scalacheck.Gen
import org.specs2.matcher.MustThrownMatchers

// TODO: this needs some more filling out
class JVMUriSpec extends Http4sSpec with MustThrownMatchers {
  "Uri relative resolution" should {

    lazy val pathSegmentGen: Gen[String] =
      Gen.oneOf(Gen.alphaNumStr, Gen.const("."), Gen.const(".."))

    lazy val pathGen: Gen[String] =
      for {
        firstPathSegment <- Gen.oneOf(Gen.const(""), pathSegmentGen)
        pathSegments <- Gen.listOf(pathSegmentGen.map(p => s"/$p"))
        lastSlash <- Gen.oneOf("", "/")
      } yield s"$firstPathSegment${pathSegments.mkString("")}$lastSlash"

    "correctly remove dot segments in other examples" >> prop { input: String =>
      val prefix = "/this/isa/prefix/"
      val processed = Uri.removeDotSegments(input)
      val path = Paths.get(prefix, processed).normalize
      path.startsWith(Paths.get(prefix)) must beTrue
      processed must not contain "./"
      processed must not contain "../"
    }.setGen(pathGen)
  }

}
