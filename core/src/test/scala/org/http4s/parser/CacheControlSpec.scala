package org.http4s.parser

import org.scalatest.{Matchers, WordSpec}
import org.http4s.Header.`Cache-Control`
import scalaz.Validation
import org.http4s.CacheDirective._
import org.http4s.CacheDirective.`s-maxage`
import org.http4s.CacheDirective.`max-stale`
import org.http4s.CacheDirective.`min-fresh`
import org.http4s.CacheDirective.`max-age`
import org.http4s.CacheDirective.`private`

/**
 * @author Bryce Anderson
 *         Created on 1/29/14
 */
class CacheControlSpec extends WordSpec with Matchers with HeaderParserHelper[`Cache-Control`] {
  def hparse(value: String): Validation[ParseErrorInfo, `Cache-Control`] = HttpParser.CACHE_CONTROL(value)

  // Default values
  val valueless = List(`no-store`, `no-transform`, `only-if-cached`,
                       `public`, `must-revalidate`, `proxy-revalidate`)

  val numberdirectives = List(`max-age`(0), `min-fresh`(1), `s-maxage`(2))

  val strdirectives = List(`private`("Foo"::Nil), `private`(Nil), `no-cache`("Foo"::Nil), `no-cache`())

  val others = List(`max-stale`(None),
                    `max-stale`(Some(2)),
                    CustomCacheDirective("Foo", None),
                    CustomCacheDirective("Foo", Some("Bar")))


  "CacheControl parser" should {

    "Generate correct directive values" in {
      valueless.foreach { v =>
        v.value should equal(v.name)
      }

      numberdirectives.zipWithIndex.foreach{ case (v, i) =>
        v.value should equal(s"${v.name}=$i")
      }

      `max-stale`(None).value should equal("max-stale")
      `max-stale`(Some(2)).value should equal("max-stale=2")

      CustomCacheDirective("Foo", Some("Bar")).value should equal("Foo=\"Bar\"")
      CustomCacheDirective("Foo", None).value should equal("Foo")

    }

    "Parse cache headers" in {
      val all = valueless ::: numberdirectives ::: strdirectives ::: others

      all.foreach { d =>
        val h = `Cache-Control`(d)
        parse(h.value) should equal(h)
      }
    }
  }
}
