package org.http4s
package headers

class LinkSpec extends HeaderLaws {
  // FIXME Uri does not round trip properly: https://github.com/http4s/http4s/issues/1651
  // checkAll(name = "Link", headerLaws(Link))

  "parse" should {
    "accept format RFC 5988" in {
      val link = """</feed>; rel="alternate"; type="text/*"; title="main"; rev="previous""""
      val parsedLinks = Link.parse(link).map(_.values)
      val parsedLink = parsedLinks.map(_.head)

      parsedLink.map(_.uri) must beRight(uri"/feed")
      parsedLink.map(_.rel) must beRight(Option("alternate"))
      parsedLink.map(_.title) must beRight(Option("main"))
      parsedLink.map(_.`type`) must beRight(Option(MediaRange.`text/*`))
      parsedLink.map(_.rev) must beRight(Option("previous"))
    }

    "accept format RFC 8288" in {
      val links = List(
        """<https://api.github.com/search/code?q=addClass&page=1>; rel="prev"""",
        """<https://api.github.com/search/code?q=addClass&page=3>; rel="next"""",
        """<https://api.github.com/search/code?q=addClass&page=4>; rel="last"""",
        """<https://api.github.com/search/code?q=addClass&page=1>; rel="first""""
      )
      val parsedLinks = Link
        .parse(links.mkString(", "))
        .map(_.values)

      parsedLinks.map(_.size) must beRight(links.size)
    }
  }

  "render" should {
    "properly format link according to RFC 5988" in {
      val links = Link(
        LinkValue(
          uri"/feed",
          rel = Some("alternate"),
          title = Some("main"),
          `type` = Some(MediaRange.`text/*`)
        ))

      links.renderString must_==
        "Link: </feed>; rel=alternate; title=main; type=text/*"
    }
  }
}
