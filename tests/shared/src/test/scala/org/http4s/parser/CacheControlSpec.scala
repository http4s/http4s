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

import org.http4s.CacheDirective._
import org.http4s.headers.`Cache-Control`
import org.typelevel.ci._

import scala.concurrent.duration._

class CacheControlSpec extends Http4sSuite with HeaderParserHelper[`Cache-Control`] {

  // Default values
  val valueless = List(
    `no-store`,
    `no-transform`,
    `only-if-cached`,
    `public`,
    `must-revalidate`,
    `proxy-revalidate`,
  )

  val numberdirectives = List(
    `max-age`(0.seconds),
    `min-fresh`(1.second),
    `s-maxage`(2.seconds),
    `stale-if-error`(3.seconds),
    `stale-while-revalidate`(4.seconds),
  )

  val strdirectives =
    List(`private`(ci"Foo" :: Nil), `private`(Nil), `no-cache`(ci"Foo" :: Nil), `no-cache`())

  val others = List(
    `max-stale`(None),
    `max-stale`(Some(2.seconds)),
    CacheDirective("Foo", None),
    CacheDirective("Foo", Some("Bar")),
  )

  test("CacheControl parser should Generate correct directive values") {
    valueless.foreach { v =>
      assertEquals(v.value, v.name.toString)
    }

    numberdirectives.zipWithIndex.foreach { case (v, i) =>
      assertEquals(v.value, s"${v.name}=$i")
    }

    assertEquals(`max-stale`(None).value, "max-stale")
    assertEquals(`max-stale`(Some(2.seconds)).value, "max-stale=2")

    assertEquals(CacheDirective("Foo", Some("Bar")).value, "Foo=\"Bar\"")
    assertEquals(CacheDirective("Foo", None).value, "Foo")
  }

  test("CacheControl parser should Parse cache headers") {
    val all = valueless ::: numberdirectives ::: strdirectives ::: others

    all.foreach { d =>
      val h = `Cache-Control`(d)
      assertEquals(roundTrip(h), h)
    }
  }

}
