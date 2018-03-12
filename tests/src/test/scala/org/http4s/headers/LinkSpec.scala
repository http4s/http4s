package org.http4s.headers

import org.http4s.MediaRange

class LinkSpec extends HeaderLaws {

  val link = """</feed>; rel="alternate"; type="text/*"; title="main"""""

  "parse" should {
    "accept format RFC 5988" in {
      val parsedLink = Link.parse(link).right
      parsedLink.map(_.uri) must beRight(uri("/feed"))
      parsedLink.map(_.rel) must beRight(Option("alternate"))
      parsedLink.map(_.title) must beRight(Option("main"))
      parsedLink.map(_.`type`) must beRight(Option(MediaRange.`text/*`))
    }
  }

  "render" should {
    "properly format link according to RFC 5988" in {
      Link(
        uri("/feed"),
        rel = Some("alternate"),
        title = Some("main"),
        `type` = Some(MediaRange.`text/*`)).renderString must_==
        "Link: </feed>; rel=alternate; title=main; type=text/*"
    }
  }
}
