package org.http4s
package parser

import org.http4s.headers.`Cache-Control`
import org.specs2.mutable.Specification
import org.http4s.CacheDirective._
import org.http4s.CacheDirective.`s-maxage`
import org.http4s.CacheDirective.`max-stale`
import org.http4s.CacheDirective.`min-fresh`
import org.http4s.CacheDirective.`max-age`
import org.http4s.CacheDirective.`private`
import scala.concurrent.duration._
import org.http4s.util.string._

class CacheControlSpec extends Specification with HeaderParserHelper[`Cache-Control`] {
  def hparse(value: String): ParseResult[`Cache-Control`] = HttpHeaderParser.CACHE_CONTROL(value)

  // Default values
  val valueless = List(`no-store`, `no-transform`, `only-if-cached`,
                       `public`, `must-revalidate`, `proxy-revalidate`)

  val numberdirectives = List(`max-age`(0.seconds), `min-fresh`(1.second), `s-maxage`(2.seconds),
                              `stale-if-error`(3.seconds), `stale-while-revalidate`(4.seconds))

  val strdirectives = List(`private`("Foo".ci::Nil), `private`(Nil), `no-cache`("Foo".ci::Nil), `no-cache`())

  val others = List(`max-stale`(None),
                    `max-stale`(Some(2.seconds)),
                    CacheDirective("Foo", None),
                    CacheDirective("Foo", Some("Bar")))


  "CacheControl parser" should {

    "Generate correct directive values" in {
      valueless.foreach { v =>
        v.value must be_==(v.name.toString)
      }

      numberdirectives.zipWithIndex.foreach{ case (v, i) =>
        v.value must be_==(s"${v.name}=$i")
      }

      `max-stale`(None).value must be_==("max-stale")
      `max-stale`(Some(2.seconds)).value must be_==("max-stale=2")

      CacheDirective("Foo", Some("Bar")).value must be_==("Foo=\"Bar\"")
      CacheDirective("Foo", None).value must be_==("Foo")
    }

    "Parse cache headers" in {
      val all = valueless ::: numberdirectives ::: strdirectives ::: others

      foreach(all) { d =>
        val h = `Cache-Control`(d)
        parse(h.value) must be_==(h)
      }
    }
  }
}
