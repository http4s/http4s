/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package parser

import org.http4s.headers.`Cache-Control`
import org.http4s.CacheDirective.{`max-age`, `max-stale`, `min-fresh`, `private`, `s-maxage`, _}
import org.specs2.mutable.Specification
import org.typelevel.ci.CIString
import scala.concurrent.duration._

class CacheControlSpec extends Specification with HeaderParserHelper[`Cache-Control`] {
  def hparse(value: String): ParseResult[`Cache-Control`] = `Cache-Control`.parse(value)

  // Default values
  val valueless = List(
    `no-store`,
    `no-transform`,
    `only-if-cached`,
    `public`,
    `must-revalidate`,
    `proxy-revalidate`)

  val numberdirectives = List(
    `max-age`(0.seconds),
    `min-fresh`(1.second),
    `s-maxage`(2.seconds),
    `stale-if-error`(3.seconds),
    `stale-while-revalidate`(4.seconds))

  val strdirectives =
    List(
      `private`(CIString("Foo") :: Nil),
      `private`(Nil),
      `no-cache`(CIString("Foo") :: Nil),
      `no-cache`())

  val others = List(
    `max-stale`(None),
    `max-stale`(Some(2.seconds)),
    CacheDirective("Foo", None),
    CacheDirective("Foo", Some("Bar")))

  "CacheControl parser" should {
    "Generate correct directive values" in {
      valueless.foreach { v =>
        v.value must be_==(v.name.toString)
      }

      numberdirectives.zipWithIndex.foreach { case (v, i) =>
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
