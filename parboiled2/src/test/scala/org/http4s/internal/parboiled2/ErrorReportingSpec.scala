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

class ErrorReportingSpec extends TestParserSpec {

  "The Parser should properly report errors" >> {

    "example 1" in new TestParser0 {
      import CharPredicate.UpperAlpha
      val hex = CharPredicate.UpperHexLetter

      def targetRule = rule {
        'a' ~ oneOrMore('b') ~ anyOf("cde") ~ ("fgh" | CharPredicate.Digit | hex | UpperAlpha) ~ noneOf("def") ~ EOI
      }

      "" must beMismatchedWithErrorMsg(
        """Unexpected end of input, expected targetRule (line 1, column 1):
          |
          |^
          |
          |1 rule mismatched at error location:
          |  /targetRule/ 'a'
          |""")

      "ax" must beMismatchedWithErrorMsg(
        """Invalid input 'x', expected 'b' (line 1, column 2):
          |ax
          | ^
          |
          |1 rule mismatched at error location:
          |  /targetRule/ + / 'b'
          |""")

      "abx" must beMismatchedWithErrorMsg(
        """Invalid input 'x', expected 'b' or [cde] (line 1, column 3):
          |abx
          |  ^
          |
          |2 rules mismatched at error location:
          |  /targetRule/ +:-1 / 'b'
          |  /targetRule/ [cde]
          |""")

      "abcx" must beMismatchedWithErrorMsg(
        """Invalid input 'x', expected 'f', Digit, hex or UpperAlpha (line 1, column 4):
          |abcx
          |   ^
          |
          |4 rules mismatched at error location:
          |  /targetRule/ | / "fgh" / 'f'
          |  /targetRule/ | / Digit:<CharPredicate>
          |  /targetRule/ | / hex:<CharPredicate>
          |  /targetRule/ | / UpperAlpha:<CharPredicate>
          |""")

      "abcfghe" must beMismatchedWithErrorMsg(
        """Invalid input 'e', expected [^def] (line 1, column 7):
          |abcfghe
          |      ^
          |
          |1 rule mismatched at error location:
          |  /targetRule/ [^def]
          |""")
    }

    "for rules with negative syntactic predicates" in new TestParser0 {
      def targetRule = rule { (!"a" ~ ANY | 'z') ~ !foo ~ EOI }
      def foo = rule { "bcd" }

      "a" must beMismatchedWithErrorMsg(
        """Invalid input 'a', expected !"a" or 'z' (line 1, column 1):
          |a
          |^
          |
          |2 rules mismatched at error location:
          |  /targetRule/ | / !"a"
          |  /targetRule/ | / 'z'
          |""")

      "xbcd" must beMismatchedWithErrorMsg(
        """Invalid input "bcd", expected !foo (line 1, column 2):
          |xbcd
          | ^
          |
          |1 rule mismatched at error location:
          |  /targetRule/ !foo
          |""")
    }

    "for rules with backtick identifiers" in new TestParser0 {
      val `this*that` = CharPredicate.Alpha
      def targetRule = rule { `foo-bar` ~ `this*that` ~ `#hash#` ~ EOI }
      def `foo-bar` = 'x'
      def `#hash#` = rule { '#' }

      "a" must beMismatchedWithErrorMsg(
        """Invalid input 'a', expected targetRule (line 1, column 1):
          |a
          |^
          |
          |1 rule mismatched at error location:
          |  /targetRule/ 'x'
          |""")

      "x" must beMismatchedWithErrorMsg(
        """Unexpected end of input, expected this*that (line 1, column 2):
          |x
          | ^
          |
          |1 rule mismatched at error location:
          |  /targetRule/ this*that:<CharPredicate>
          |""")

      "xyz" must beMismatchedWithErrorMsg(
        """Invalid input 'z', expected #hash# (line 1, column 3):
          |xyz
          |  ^
          |
          |1 rule mismatched at error location:
          |  /targetRule/ /#hash#/ '#'
          |""")
    }

    "if the error location is the newline at line-end" in new TestParser0 {
      def targetRule = rule { "abc" ~ EOI }

      "ab\nc" must beMismatchedWithErrorMsg(
        """Invalid input '\n', expected 'c' (line 1, column 3):
          |ab
          |  ^
          |
          |1 rule mismatched at error location:
          |  /targetRule/ "abc":-2 / 'c'
          |""")
    }

    "for rules with an explicitly attached name" in new TestParser0 {
      def targetRule = namedRule("foo") { "abc".named("prefix") ~ ("def" | "xyz").named("suffix") ~ EOI }

      "abx" must beMismatchedWithErrorMsg(
        """Invalid input 'x', expected 'c' (line 1, column 3):
          |abx
          |  ^
          |
          |1 rule mismatched at error location:
          |  /foo/ prefix:"abc":-2 / 'c'
          |""")

      "abc-" must beMismatchedWithErrorMsg(
        """Invalid input '-', expected 'd' or 'x' (line 1, column 4):
          |abc-
          |   ^
          |
          |2 rules mismatched at error location:
          |  /foo/ suffix:| / "def" / 'd'
          |  /foo/ suffix:| / "xyz" / 'x'
          |""")
    }

    "for rules containing `fail`" in new TestParser0 {
      def targetRule = rule { "foo" | fail("something cool") }

      "x" must beMismatchedWithErrorMsg(
        """Invalid input 'x', expected something cool (line 1, column 1):
          |x
          |^
          |
          |1 rule mismatched at error location:
          |  something cool
          |""")

      "foo" must beMatched
    }

    "respecting the `errorTraceCollectionLimit`" in new TestParser0 {
      def targetRule = rule { "a" | 'b' | "c" | "d" | "e" | "f" }
      override def errorTraceCollectionLimit = 3

      "x" must beMismatchedWithErrorMsg(
        """Invalid input 'x', expected 'a', 'b' or 'c' (line 1, column 1):
          |x
          |^
          |
          |3 rules mismatched at error location:
          |  /targetRule/ | / "a" / 'a'
          |  /targetRule/ | / 'b'
          |  /targetRule/ | / "c" / 'c'
          |""")
    }

    "respecting `atomic` markers (example 1)" in new TestParser0 {
      def targetRule = rule { ch('-').* ~ (atomic("foo") | atomic("bar") | atomic("baz")) }

      "---fox" must beMismatchedWithErrorMsg(
        """Invalid input "fox", expected '-', "foo", "bar" or "baz" (line 1, column 4):
          |---fox
          |   ^
          |
          |4 rules mismatched at error location:
          |  /targetRule/ *:-3 / '-'
          |  /targetRule/ | / atomic / "foo":-2 / 'o'
          |  /targetRule/ | / atomic / "bar" / 'b'
          |  /targetRule/ | / atomic / "baz" / 'b'
          |""")
    }

    "respecting `atomic` markers (example 2)" in new TestParser0 {
      def targetRule = rule { atomic(ch('a') | 'b') }

      "c" must beMismatchedWithErrorMsg(
        """Invalid input 'c', expected targetRule (line 1, column 1):
          |c
          |^
          |
          |2 rules mismatched at error location:
          |  /targetRule/ atomic / | / 'a'
          |  /targetRule/ atomic / | / 'b'
          |""")
    }

    "respecting `quiet` markers" in new TestParser0 {
      def targetRule = rule { "abc" ~ (quiet("dxy") | "def") }

      // quiet rule mismatch must be suppressed
      "abcd-" must beMismatchedWithErrorMsg(
        """Invalid input '-', expected 'e' (line 1, column 5):
          |abcd-
          |    ^
          |
          |1 rule mismatched at error location:
          |  /targetRule/ |:-1 / "def":-1 / 'e'
          |""")

      // since the error location is only reached by a quiet rule we need to report it
      "abcdx" must beMismatchedWithErrorMsg(
        """Unexpected end of input, expected 'y' (line 1, column 6):
          |abcdx
          |     ^
          |
          |1 rule mismatched at error location:
          |  /targetRule/ |:-2 / quiet:-2 / "dxy":-2 / 'y'
          |""")
    }

    "expanding tabs as configured" in new TestParser0 {
      def targetRule = rule { ch('\t').* ~ (atomic("foo") | atomic("bar") | atomic("baz")) }

      override def errorFormatter = new ErrorFormatter(expandTabs = 4, showTraces = true)

      "\t\t\tfox\t\tbar" must beMismatchedWithErrorMsg(
        """Invalid input "fox", expected '\t', "foo", "bar" or "baz" (line 1, column 4):
          |            fox     bar
          |            ^
          |
          |4 rules mismatched at error location:
          |  /targetRule/ *:-3 / '\t'
          |  /targetRule/ | / atomic / "foo":-2 / 'o'
          |  /targetRule/ | / atomic / "bar" / 'b'
          |  /targetRule/ | / atomic / "baz" / 'b'
          |""")
    }
  }
}
