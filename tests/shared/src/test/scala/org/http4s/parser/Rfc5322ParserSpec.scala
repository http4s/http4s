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

package org.http4s.parser

import org.http4s.Http4sSuite
import org.http4s.internal.parsing.Rfc5322

class Rfc5322ParserSpec extends Http4sSuite {
  test("FWS parser") {
    val cases = List(
      " ",
      "  ",
      "\r\n  ",
      "   \r\n   ",
    )
    cases.foreach(c => assert(Rfc5322.FWS.parse(c).isRight))
    val failCases = List(
      "",
      "\r\n",
      "  \r\n",
    )
    failCases.foreach(c => assert(Rfc5322.FWS.parse(c).isLeft))
  }

  test("quoted-pair parser") {
    val cases = List(
      ("\\!", "!"),
      ("\\ ", " "),
    )
    cases.foreach(c => assertEquals(Rfc5322.`quoted-pair`.parse(c._1).toOption.get._2, c._2))
  }

  test("comment parser") {
    val cases = List(
      "(\\!)",
      "(\\! )",
      "( \\!)",
      "( \\! )",
      "( \\!   \\!   )",
      "( \\! comment )",
      "(    \r\n  comment)",
      "( comment1 comment2 ( comment3 ))",
      "( \\! comment (((comment2))) ((comment3 \r\n (comment4 \\$))) )",
    )
    cases.foreach(c => assert(Rfc5322.comment.parse(c).isRight))
  }

  test("CFWS parser") {
    val cases = List(
      " ",
      " ( comment ) ",
      " ( comment )  (comment 2) ",
    )
    cases.foreach(c => assert(Rfc5322.CFWS.parse(c).isRight))
  }

  test("atom parser") {
    val cases = List(
      ("abcd1234", "abcd1234"),
      ("(comment 1) abcd1234 (comment 2)", "abcd1234"),
    )
    cases.foreach(c => assertEquals(Rfc5322.atom.parse(c._1).toOption.get._2, c._2))
  }

  test("dot-atom-text parser") {
    val cases = List(
      ("a", "a"),
      ("a.b", "a.b"),
      ("abc.defg", "abc.defg"),
      ("a.b.c.defg", "a.b.c.defg"),
    )
    cases.foreach(c => assertEquals(Rfc5322.`dot-atom-text`.parse(c._1).toOption.get._2, c._2))

    val failCases = List(
      ".abc"
    )
    failCases.foreach(c => assert(Rfc5322.`dot-atom-text`.parse(c).isLeft))
  }

  test("quoted-string parser") {
    val cases = List(
      ("\"a\"", "a"),
      (" (comment1) \"abc\\! dfg\\!\" (comment2) ", "abc! dfg!"),
    )
    cases.foreach(c => assertEquals(Rfc5322.`quoted-string`.parse(c._1).toOption.get._2, c._2))
    val failCases = List(
      "a"
    )
    failCases.foreach(c => assertEquals(Rfc5322.`quoted-string`.parse(c).toOption, None))
  }

  test("domain-literal parser") {
    val cases = List(
      (" (comment) [ 1 2 3 4 ] (comment) ", "[ 1 2 3 4 ]"),
      ("[]", "[]"),
      ("[example.com]", "[example.com]"),
    )
    cases.foreach(c => assertEquals(Rfc5322.`domain-literal`.parse(c._1).toOption.get._2, c._2))
  }

  test("addr-spec parser") {
    val cases = List(
      ("abc@d.com", "abc@d.com"),
      ("a.b.c.d@e.com", "a.b.c.d@e.com"),
      ("\"abc\"@d.e.f", "abc@d.e.f"),
    )
    cases.foreach(c => assertEquals(Rfc5322.`addr-spec`.parse(c._1).toOption.get._2, c._2))
  }

  test("mailbox parser") {
    val cases = List(
      ("abc <d.e.f.g@hijk.com>", "d.e.f.g@hijk.com"),
      ("<d.e.f.g@hijk.com>", "d.e.f.g@hijk.com"),
      ("abc@hijk.com", "abc@hijk.com"),
    )
    cases.foreach(c => assertEquals(Rfc5322.mailbox.parse(c._1).toOption.get._2, c._2))
    val failCases = List(
      "abc.example.com",
      "a@c@example.com",
    )
    failCases.foreach(c => assert(Rfc5322.mailbox.parse(c).isLeft))
  }
}
