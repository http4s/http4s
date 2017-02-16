/*
 * Copyright (C) 2009-2013 Mathias Doenitz, Alexander Myltsev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.internal.parboiled2

class CombinatorSpec extends TestParserSpec {

  "The Parser should correctly recognize/reject input for the" >> {

    "`~` combinator" in new TestParser0 {
      def targetRule = rule { 'a' ~ 'b' }
      "" must beMismatched
      "ab" must beMatched
      "ac" must beMismatched
      "a" must beMismatched
      "b" must beMismatched
    }

    "`|` combinator" in new TestParser0 {
      def targetRule = rule { ch('a') | 'b' }
      "" must beMismatched
      "a" must beMatched
      "b" must beMatched
      "c" must beMismatched
    }

    "`zeroOrMore(Rule0)` modifier" in new TestParser0 {
      def targetRule = rule { zeroOrMore("a") ~ EOI }
      "" must beMatched
      "a" must beMatched
      "aa" must beMatched
      "b" must beMismatched
    }

    "`Rule0.*` modifier" in new TestParser0 {
      def targetRule = rule { str("a").* ~ EOI }
      "" must beMatched
      "a" must beMatched
      "aa" must beMatched
      "b" must beMismatched
    }

    "`zeroOrMore(Rule0).separatedBy('|')` modifier" in new TestParser0 {
      def targetRule = rule { zeroOrMore("a").separatedBy('|') ~ EOI }
      "" must beMatched
      "a" must beMatched
      "a|a" must beMatched
      "a|a|" must beMismatched
      "aa" must beMismatched
      "b" must beMismatched
    }

    "`Rule0.*.sep('|')` modifier" in new TestParser0 {
      def targetRule = rule { str("a").*('|') ~ EOI }
      "" must beMatched
      "a" must beMatched
      "a|a" must beMatched
      "a|a|" must beMismatched
      "aa" must beMismatched
      "b" must beMismatched
    }

    "`zeroOrMore(Rule1[T])` modifier" in new TestParser1[Seq[String]] {
      def targetRule = rule { zeroOrMore(capture("a")) ~ EOI }
      "a" must beMatchedWith(Seq("a"))
      "aa" must beMatchedWith(Seq("a", "a"))
      "b" must beMismatched
      "" must beMatchedWith(Seq.empty)
    }

    "`zeroOrMore(Rule[I, O <: I])` modifier" in new TestParser1[String] {
      def targetRule = rule { capture("a") ~ zeroOrMore(ch('x') ~> ((_: String) + 'x')) ~ EOI }
      "a" must beMatchedWith("a")
      "ax" must beMatchedWith("ax")
      "axx" must beMatchedWith("axx")
    }

    "`oneOrMore(Rule0)` modifier" in new TestParser0 {
      def targetRule = rule { oneOrMore("a") ~ EOI }
      "a" must beMatched
      "aa" must beMatched
      "b" must beMismatched
      "" must beMismatched
    }

    "`oneOrMore(Rule0).separatedBy('|')` modifier" in new TestParser0 {
      def targetRule = rule { oneOrMore("a").separatedBy('|') ~ EOI }
      "" must beMismatched
      "a" must beMatched
      "a|a" must beMatched
      "a|a|" must beMismatched
      "aa" must beMismatched
      "b" must beMismatched
    }

    "`oneOrMore(Rule1[T])` modifier" in new TestParser1[Seq[String]] {
      def targetRule = rule { oneOrMore(capture("a")) ~ EOI }
      "a" must beMatchedWith(Seq("a"))
      "aa" must beMatchedWith(Seq("a", "a"))
      "b" must beMismatched
      "" must beMismatched
    }

    "`Rule1[T].+` modifier" in new TestParser1[Seq[String]] {
      def targetRule = rule { capture("a").+ ~ EOI }
      "a" must beMatchedWith(Seq("a"))
      "aa" must beMatchedWith(Seq("a", "a"))
      "b" must beMismatched
      "" must beMismatched
    }

    "`oneOrMore(Rule[I, O <: I])` modifier" in new TestParser1[String] {
      def targetRule = rule { capture("a") ~ oneOrMore(ch('x') ~> ((_: String) + 'x')) ~ EOI }
      "a" must beMismatched
      "ax" must beMatchedWith("ax")
      "axx" must beMatchedWith("axx")
    }

    "`optional(Rule0)` modifier" in new TestParser0 {
      def targetRule = rule { optional("a") ~ EOI }
      "a" must beMatched
      "b" must beMismatched
      "" must beMatched
    }

    "`optional(Rule1[T])` modifier" in new TestParser1[Option[String]] {
      def targetRule = rule { optional(capture("a")) ~ EOI }
      "a" must beMatchedWith(Some("a"))
      "" must beMatchedWith(None)
      "b" must beMismatched
      "ab" must beMismatched
    }

    "`optional(Rule[I, O <: I])` modifier" in new TestParser1[String] {
      def targetRule = rule { capture("a") ~ optional(ch('x') ~> ((_: String) + 'x')) ~ EOI }
      "a" must beMatchedWith("a")
      "ax" must beMatchedWith("ax")
      "axx" must beMismatched
    }

    "`Rule[I, O <: I].?` modifier" in new TestParser1[String] {
      def targetRule = rule { capture("a") ~ (ch('x') ~> ((_: String) + 'x')).? ~ EOI }
      "a" must beMatchedWith("a")
      "ax" must beMatchedWith("ax")
      "axx" must beMismatched
    }

    "`!(Rule0)` modifier" in new TestParser0 {
      def targetRule = rule { !"a" }
      "a" must beMismatched
      "b" must beMatched
      "" must beMatched
    }

    "`&` modifier" in new TestParser0 {
      def targetRule = rule { &("a") }
      "a" must beMatched
      cursor === 0
      "b" must beMismatched
      "" must beMismatched
    }

    "`1.times(Rule0)` modifier" in new TestParser0 {
      def targetRule = rule { 1.times("a") }
      "a" must beMatched
      "" must beMismatched
    }

    "`2.times(Rule0)` modifier (example 1)" in new TestParser0 {
      def targetRule = rule { 2.times("x") }
      "" must beMismatched
      "x" must beMismatched
      "xx" must beMatched
      "xxx" must beMatched
    }

    "`n.times(Rule0)` modifier (example 2)" in new TestParser0 {
      val n = 2
      def targetRule = rule { n.times("x") ~ EOI }
      "x" must beMismatched
      "xx" must beMatched
      "xxx" must beMismatched
    }

    "`2.times(Rule0).separatedBy('|')` modifier" in new TestParser0 {
      def targetRule = rule { 2.times("x").separatedBy('|') ~ EOI }
      "xx" must beMismatched
      "x|x" must beMatched
      "x|x|" must beMismatched
    }

    "`(2 to 4).times(Rule0)` modifier (example 1)" in new TestParser0 {
      def targetRule = rule { (2 to 4).times("x") }
      "" must beMismatched
      "x" must beMismatched
      "xx" must beMatched
      "xxx" must beMatched
      "xxxx" must beMatched
      "xxxxx" must beMatched
    }

    "`(2 to 4).times(Rule0)` modifier (example 2)" in new TestParser0 {
      def targetRule = rule { (2 to 4).times("x") ~ EOI }
      "xx" must beMatched
      "xxx" must beMatched
      "xxxx" must beMatched
      "xxxxx" must beMismatched
    }

    "`(2 to max).times(Rule0)` modifier where `max` is 4" in new TestParser0 {
      val max = 4
      def targetRule = rule { (2 to max).times("x") ~ EOI }
      "xx" must beMatched
      "xxx" must beMatched
      "xxxx" must beMatched
      "xxxxx" must beMismatched
    }

    "`(2 to 4).times(Rule0).separatedBy('|')` modifier" in new TestParser0 {
      def targetRule = rule { (2 to 4).times("x").separatedBy('|') ~ EOI }
      "xx" must beMismatched
      "x|" must beMismatched
      "x|x" must beMatched
      "x|x|" must beMismatched
      "x|x|x" must beMatched
      "x|x|x|" must beMismatched
      "x|x|x|x" must beMatched
      "x|x|x|x|" must beMismatched
      "x|x|x|x|x" must beMismatched
    }

    "`(1 to 3).times(Rule1[T])` modifier" in new TestParser1[Seq[String]] {
      def targetRule = rule { (1 to 3).times(capture("x")) ~ EOI }
      "" must beMismatched
      "x" must beMatchedWith(Seq("x"))
      "xx" must beMatchedWith(Seq("x", "x"))
      "xxx" must beMatchedWith(Seq("x", "x", "x"))
      "xxxx" must beMismatched
    }

    "`2.times(Rule[I, O <: I])` modifier" in new TestParser1[String] {
      def targetRule = rule { capture("a") ~ 2.times(ch('x') ~> ((_: String) + 'x')) ~ EOI }
      "a" must beMismatched
      "ax" must beMismatched
      "axx" must beMatchedWith("axx")
      "axxx" must beMismatched
    }
  }
}
