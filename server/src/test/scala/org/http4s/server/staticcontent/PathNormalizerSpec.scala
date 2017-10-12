package org.http4s.server.staticcontent

import java.nio.file.Paths
import org.scalacheck.Gen
import org.specs2.ScalaCheck
import org.specs2.mutable.{Specification, Tables}

class PathNormalizerSpec extends Specification with Tables with ScalaCheck {

  "PathNormalizer.removeDotSegments" should {
    // Examples adapted from RFC3986, section 5.4
    "remove dot segments correctly in RFC examples" >> {
      "path" | "expected" |>
        "/a/b/c/./../../g" ! "/a/g" |
        "mid/content=5/../6" ! "mid/6" |
        "/a/b/c/./g" ! "/a/b/c/g" |
        "/a/b/c/g/" ! "/a/b/c/g/" |
        "/a/b/c/g" ! "/a/b/c/g" |
        "/a/b/c/../../../g" ! "/g" |
        "/a/b/c/../../../../g" ! "/g" |
        "/a/b/c/." ! "/a/b/c/" |
        "/a/b/c/./" ! "/a/b/c/" |
        "/a/b/c/.." ! "/a/b/" |
        "/a/b/c/../" ! "/a/b/" |
        "/a/b/c/../g" ! "/a/b/g" |
        "/a/b/c/../.." ! "/a/" |
        "/a/b/c/../../" ! "/a/" |
        "/a/b/c/../../g" ! "/a/g" |
        "/a/b/c/../../../g" ! "/g" |
        "/a/b/c/../../../../g" ! "/g" |
        "/a/b/c/g." ! "/a/b/c/g." |
        "/a/b/c/.g" ! "/a/b/c/.g" |
        "/a/b/c/g.." ! "/a/b/c/g.." |
        "/a/b/c/..g" ! "/a/b/c/..g" |
        "/a/b/c/./../g" ! "/a/b/g" |
        "/a/b/c/./g/." ! "/a/b/c/g/" |
        "/a/b/c/g/./h" ! "/a/b/c/g/h" |
        "/a/b/c/g/../h" ! "/a/b/c/h" |
        "/a/b/c/g;x=1/./y" ! "/a/b/c/g;x=1/y" |
        "/a/b/c/g;x=1/../y" ! "/a/b/c/y" | { (path, expected) =>
        PathNormalizer.removeDotSegments(path) must_=== expected
      }
    }

    "remove dot segments correctly in other examples" >> prop { input: String =>
      val prefix    = "/this/isa/prefix/"
      val processed = PathNormalizer.removeDotSegments(input)
      val path      = Paths.get(prefix, processed).normalize

      path.startsWith(Paths.get(prefix)) must beTrue
      processed must not contain "./"
      processed must not contain "../"
    }.setGen(pathGen)
  }

  lazy val maybeSlashGen: Gen[String] = Gen.oneOf("", "/")

  lazy val pathSegmentGen: Gen[String] =
    for {
      str  <- Gen.alphaNumStr
      path <- Gen.oneOf(str, ".", "..")
    } yield s"/$path"

  lazy val pathGen: Gen[String] =
    for {
      firstSlash   <- maybeSlashGen
      pathSegments <- Gen.listOf(pathSegmentGen)
      lastSlash    <- maybeSlashGen
    } yield s"$firstSlash${pathSegments.mkString("")}$lastSlash"

}
