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
package headers

import org.http4s.syntax.all._

class LinkSuite extends HeaderLaws {
  // FIXME Uri does not round trip properly: https://github.com/http4s/http4s/issues/1651
  // checkAll(name = "Link", headerLaws[Link])

  test("parse should accept format RFC 5988") {
    val link = """</feed>; rel="alternate"; type="text/*"; title="main"; rev="previous""""
    val parsedLinks = Link.parse(link).map(_.values)
    val parsedLink = parsedLinks.map(_.head)

    assertEquals(parsedLink.map(_.uri), Right(uri"/feed"))
    assertEquals(parsedLink.map(_.rel), Right(Option("alternate")))
    assertEquals(parsedLink.map(_.title), Right(Option("main")))
    assertEquals(parsedLink.map(_.`type`), Right(Option(MediaRange.`text/*`)))
    assertEquals(parsedLink.map(_.rev), Right(Option("previous")))
  }

  test("parse should accept format RFC 8288") {
    val links = List(
      """<https://api.github.com/search/code?q=addClass&page=1>; rel="prev"""",
      """<https://api.github.com/search/code?q=addClass&page=3>; rel="next"""",
      """<https://api.github.com/search/code?q=addClass&page=4>; rel="last"""",
      """<https://api.github.com/search/code?q=addClass&page=1>; rel="first"""",
    )
    val parsedLinks = Link
      .parse(links.mkString(", "))
      .map(_.values)

    assertEquals(parsedLinks.map(_.size), Right(links.size))
  }

  test(
    "parse should retain the value of the first 'rel' param when multiple present (RFC 8288, Section 3.3)"
  ) {
    val link = """<http://example.com/foo>; rel=prev; rel=next;title=foo; rel="last""""
    val parsedLinks = Link.parse(link).map(_.values)
    val parsedLink = parsedLinks.map(_.head)

    assertEquals(parsedLink.map(_.uri), Right(uri"http://example.com/foo"))
    assertEquals(parsedLink.map(_.rel), Right(Option("prev")))
    assertEquals(parsedLink.map(_.title), Right(Option("foo")))
  }

  test("render should properly format link according to RFC 5988") {
    val links = Link(
      LinkValue(
        uri"/feed",
        rel = Some("alternate"),
        title = Some("main"),
        `type` = Some(MediaRange.`text/*`),
      )
    )

    assertEquals(links.renderString, "Link: </feed>; rel=alternate; title=main; type=text/*")
  }
}
