package org.http4s.parser

import org.http4s.Http4sSuite
import org.http4s.internal.parsing.Rfc5322

class Rfc5322ParserSpec extends Http4sSuite {
  test("FWS parser") {
    val cases = List(
      " ",
      "  ",
      "\r\n  ",
      "   \r\n   "
    )
    cases.foreach(c => {
      assert(Rfc5322.FWS.parse(c).isRight)
    })
    val cases2 = List(
      "",
      "  \r\n",
      "\r\n"
    )
    cases2.foreach(c => {
      assert(Rfc5322.FWS.parse(c).isLeft)
    })
  }

  test("quoted-pair parser") {
    val cases = List(
      ("\\!", "!"),
      ("\\ ", " ")
    )
    cases.foreach(c => {
      assertEquals(Rfc5322.`quoted-pair`.parse(c._1).toOption.get._2, c._2)
    })
  }

  test("comment parser") {
    val cases = List(
      ("( comment1 comment2 ( comment3 ))", "( comment1 comment2 ( comment3 ))"),
      ("(    \r\n  comment)", "(    \r\n  comment)"),
      ("( \\! )", "( ! )"),
      ("(\\! )", "(! )"),
      ("( \\!)", "( !)"),
      ("(\\!)", "(!)"),
      ("( \\!   \\!   )", "( !   !   )"),
      ("( \\! comment )", "( ! comment )"),
      ("( \\! comment (((comment2))) ((comment3 \r\n (comment4 \\$))) )", "( ! comment (((comment2))) ((comment3 \r\n (comment4 $))) )")
    )
    cases.foreach(c => {
      assertEquals(Rfc5322.comment.parse(c._1).toOption.get._2, c._2)
    })
  }

  test("CFWS parser") {
    val cases = List(
      (" ", " "),
      (" ( comment ) ", " ( comment ) "),
      (" ( comment )  (comment 2) ", " ( comment )  (comment 2) ")
    )
    cases.foreach(c => {
      assertEquals(Rfc5322.CFWS.parse(c._1).toOption.get._2, c._2)
    })
  }
}
