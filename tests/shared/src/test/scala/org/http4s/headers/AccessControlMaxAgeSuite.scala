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

package org.http4s.headers

import org.http4s.Http4sSuite
import org.http4s.ParseFailure
import org.http4s.ParseResult
import org.http4s.implicits.http4sSelectSyntaxOne

import scala.concurrent.duration.DurationInt

class AccessControlMaxAgeSuite extends Http4sSuite {

  test("render should create and header with age in seconds") {
    assertEquals(
      `Access-Control-Max-Age`.fromLong(120).map(_.renderString),
      ParseResult.success("Access-Control-Max-Age: 120"),
    )
  }

  test("render should create and header with caching disable (-1)") {
    assertEquals(
      `Access-Control-Max-Age`.fromLong(-1).map(_.renderString),
      ParseResult.success("Access-Control-Max-Age: -1"),
    )
  }

  test("build should build correctly for positives") {
    val parseResult = `Access-Control-Max-Age`.fromLong(10)
    assertEquals(parseResult, Right(`Access-Control-Max-Age`.Cache(10)))
  }

  test("build should build correctly for value -1") {
    val parseResult = `Access-Control-Max-Age`.fromLong(-1)
    assertEquals(parseResult, Right(`Access-Control-Max-Age`.NoCaching))
  }

  test("build should fail for negatives other than -1") {
    val parseResult = `Access-Control-Max-Age`.fromLong(-10)
    assert(parseResult.isLeft)
  }

  test("build should build unsafe for positives") {
    assertEquals(
      `Access-Control-Max-Age`.unsafeFromDuration(10.seconds),
      `Access-Control-Max-Age`.Cache(10),
    )
    assertEquals(
      `Access-Control-Max-Age`.unsafeFromLong(10),
      `Access-Control-Max-Age`.Cache(10),
    )

    assertEquals(
      `Access-Control-Max-Age`.unsafeFromDuration(-1.seconds),
      `Access-Control-Max-Age`.NoCaching,
    )
    assertEquals(
      `Access-Control-Max-Age`.unsafeFromLong(-1),
      `Access-Control-Max-Age`.NoCaching,
    )
  }

  test("build should fail unsafe for negatives other than -1") {
    intercept[ParseFailure](`Access-Control-Max-Age`.unsafeFromDuration(-10.seconds))
    intercept[ParseFailure](`Access-Control-Max-Age`.unsafeFromLong(-10))
  }

  test("produce should safe") {
    val cacheHeader =
      `Access-Control-Max-Age`.unsafeFromLong(10).asInstanceOf[`Access-Control-Max-Age`.Cache]
    assertEquals(cacheHeader.duration, Option(10.seconds))
  }
  test("produce should unsafe") {
    val cacheHeader =
      `Access-Control-Max-Age`.unsafeFromLong(10).asInstanceOf[`Access-Control-Max-Age`.Cache]
    assertEquals(cacheHeader.unsafeDuration, 10.seconds)
  }

  test("parse should accept duration in seconds") {
    assertEquals(`Access-Control-Max-Age`.parse("120"), Right(`Access-Control-Max-Age`.Cache(120)))
  }

  test("parse should accept the value -1") {
    assertEquals(`Access-Control-Max-Age`.parse("-1"), Right(`Access-Control-Max-Age`.NoCaching))
  }

  test("parse should reject negative values") {
    assert(`Access-Control-Max-Age`.parse("-120").isLeft)
  }
}
