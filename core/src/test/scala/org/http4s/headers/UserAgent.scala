package org.http4s
package headers


class UserAgent extends HeaderParserSpec(`User-Agent`) {

  "User-Agent" should {
    "parse User-Agent" in {
      val header = `User-Agent`(AgentProduct("foo", Some("bar")), Seq(AgentComment("foo")))
      header.value must_== "foo/bar (foo)"

      hparse(header.value) must_== Some(header)

      val header2 = `User-Agent`(AgentProduct("foo"), Seq(AgentProduct("bar", Some("biz")), AgentComment("blah")))
      header2.value must_== "foo bar/biz (blah)"
      hparse(header2.value) must_== Some(header2)

      val headerstr = "Mozilla/5.0 (Android; Mobile; rv:30.0) Gecko/30.0 Firefox/30.0"
      hparse(Header.Raw(`User-Agent`.name, headerstr).value) must_== Some(
        `User-Agent`(AgentProduct("Mozilla", Some("5.0")), Seq(
            AgentComment("Android; Mobile; rv:30.0"),
            AgentProduct("Gecko", Some("30.0")),
            AgentProduct("Firefox", Some("30.0"))
          )
        )
      )
    }
  }
}
