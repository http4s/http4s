package org.http4s
package headers

class LinkSpec extends HeaderLaws {
  // FIXME Uri does not round trip properly: https://github.com/http4s/http4s/issues/1651
  // checkAll(name = "Link", headerLaws(Link))

  val link = """</feed>; rel="alternate"; type="text/*"; title="main"; rev="previous""""

  "parse" should {
    "accept format RFC 5988" in {
      val parsedLink = Link.parse(link).right
      parsedLink.map(_.uri) must beRight(Uri.uri("/feed"))
      parsedLink.map(_.rel) must beRight(Option("alternate"))
      parsedLink.map(_.title) must beRight(Option("main"))
      parsedLink.map(_.`type`) must beRight(Option(MediaRange.`text/*`))
      parsedLink.map(_.rev) must beRight(Option("previous"))
    }
  }

  "render" should {
    "properly format link according to RFC 5988" in {
      Link(
        Uri.uri("/feed"),
        rel = Some("alternate"),
        title = Some("main"),
        `type` = Some(MediaRange.`text/*`)).renderString must_==
        "Link: </feed>; rel=alternate; title=main; type=text/*"
    }
  }
}
