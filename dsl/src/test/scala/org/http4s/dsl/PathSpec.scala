/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/twitter/finagle/blob/6e2462acc32ac753bf4e9d8e672f9f361be6b2da/finagle-http/src/test/scala/com/twitter/finagle/http/path/PathSpec.scala
 * Copyright 2017, Twitter Inc.
 */

package org.http4s
package dsl

import cats.effect.IO
import org.http4s.Uri.uri
import org.http4s.Uri.Path
import org.http4s.Uri.Path.{Root, Segment}
import org.http4s.dsl.io._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen

class PathSpec extends Http4sSpec {
  implicit val arbitraryPath: Gen[Path] =
    arbitrary[List[String]]
      .map(_.foldLeft(Path.Root)((acc, e) => acc.copy(segments = acc.segments :+ Segment(e))))

  "Path" should {
    "~ extractor on Path without Root" in {
      (Path.fromString("foo.json") match {
        case Path.empty / "foo" ~ "json" => true
        case _ => false
      }) must beTrue
    }

    "~ extractor on Path with Root" in {
      (Path.fromString("/foo.json") match {
        case Root / "foo" ~ "json" => true
        case _ => false
      }) must beTrue
    }

    "~ extractor on filename foo.json" in {
      ("foo.json" match {
        case "foo" ~ "json" => true
        case _ => false
      }) must beTrue
    }

    "~ extractor on filename foo" in {
      ("foo" match {
        case "foo" ~ "" => true
        case _ => false
      }) must beTrue
    }

    "-> extractor /test.json" in {
      val req = Request[IO](method = Method.GET, uri = uri("/test.json"))
      (req match {
        case GET -> Root / "test.json" => true
        case _ => false
      }) must beTrue
    }

    "-> extractor /foo/test.json" in {
      val req = Request[IO](method = Method.GET, uri = uri("/foo/test.json"))
      (req match {
        case GET -> Root / "foo" / "test.json" => true
        case _ => false
      }) must beTrue
    }

    "→ extractor /test.json" in {
      val req = Request[IO](method = Method.GET, uri = uri("/test.json"))
      (req match {
        case GET → (Root / "test.json") => true
        case _ => false
      }) must beTrue
    }

    "request path info extractor for /" in {
      val req = Request[IO](method = Method.GET, uri = uri("/"))
      (req match {
        case _ -> Root => true
        case _ => false
      }) must beTrue
    }

    "/ extractor" in {
      (Path.fromString("/1/2/3/test.json") match {
        case Root / "1" / "2" / "3" / "test.json" => true
        case _ => false
      }) must beTrue
    }

    "/: extractor" in {
      (Path.fromString("/1/2/3/test.json") match {
        case "1" /: "2" /: path => Some(path)
        case _ => None
      }) must_== Some(Path.fromString("3/test.json"))
    }

    "/: should not crash without trailing slash" in {
      // Bug reported on Gitter
      Path.fromString("/cameras/1NJDOI") match {
        case "cameras" /: _ /: "events" /: _ /: "exports" /: _ => false
        case _ => true
      }
    }

    "trailing slash" in {
      (Path.fromString("/1/2/3/") match {
        case Root / "1" / "2" / "3" / "" => true
        case _ => false
      }) must beTrue
    }

    "encoded chars" in {
      (Path.fromString("/foo%20bar/and%2For/1%2F2") match {
        case Root / "foo bar" / "and/or" / "1/2" => true
        case _ => false
      }) must beTrue
    }

    "Int extractor" in {
      (Path.fromString("/user/123") match {
        case Root / "user" / IntVar(userId) => userId == 123
        case _ => false
      }) must beTrue
    }

    "Int extractor, invalid int" in {
      (Path.fromString("/user/invalid") match {
        case Root / "user" / IntVar(userId @ _) => true
        case _ => false
      }) must beFalse
    }

    "Int extractor, number format error" in {
      (Path.fromString("/user/2147483648") match {
        case Root / "user" / IntVar(userId @ _) => true
        case _ => false
      }) must beFalse
    }

    "Long extractor" >> {
      "valid" >> {
        "small positive number" in {
          (Path.fromString("/user/123") match {
            case Root / "user" / LongVar(userId) => userId == 123
            case _ => false
          }) must beTrue
        }
        "negative number" in {
          (Path.fromString("/user/-432") match {
            case Root / "user" / LongVar(userId) => userId == -432
            case _ => false
          }) must beTrue
        }
      }
      "invalid" >> {
        "a word" in {
          (Path.fromString("/user/invalid") match {
            case Root / "user" / LongVar(userId @ _) => true
            case _ => false
          }) must beFalse
        }
        "number but out of domain" in {
          (Path.fromString("/user/9223372036854775808") match {
            case Root / "user" / LongVar(userId @ _) => true
            case _ => false
          }) must beFalse
        }
      }
    }

    "UUID extractor" >> {
      "valid" >> {
        "a UUID" in {
          (Path.fromString("/user/13251d88-7a73-4fcf-b935-54dfae9f023e") match {
            case Root / "user" / UUIDVar(userId) =>
              userId.toString == "13251d88-7a73-4fcf-b935-54dfae9f023e"
            case _ => false
          }) must beTrue
        }
      }
      "invalid" >> {
        "a number" in {
          (Path.fromString("/user/123") match {
            case Root / "user" / UUIDVar(userId @ _) => true
            case _ => false
          }) must beFalse
        }
        "a word" in {
          (Path.fromString("/user/invalid") match {
            case Root / "user" / UUIDVar(userId @ _) => true
            case _ => false
          }) must beFalse
        }
        "a bad UUID" in {
          (Path.fromString("/user/13251d88-7a73-4fcf-b935") match {
            case Root / "user" / UUIDVar(userId @ _) => true
            case _ => false
          }) must beFalse
        }
      }
    }

    "Matrix extractor" >> {
      object BoardExtractor extends impl.MatrixVar("square", List("x", "y"))

      object EmptyNameExtractor extends impl.MatrixVar("", List("x", "y"))

      object EmptyExtractor extends impl.MatrixVar("square", List.empty[String])

      "valid" >> {
        "a matrix var" in {
          (Path.fromString("/board/square;x=42;y=0") match {
            case Root / "board" / BoardExtractor(x, y) if x == "42" && y == "0" => true
            case _ => false
          }) must beTrue
        }

        "a matrix var with empty axis segment" in {
          (Path.fromString("/board/square;x=42;;y=0") match {
            case Root / "board" / BoardExtractor(x, y) if x == "42" && y == "0" => true
            case _ => false
          }) must beTrue
        }

        "a matrix var with empty trailing axis segment" in {
          (Path.fromString("/board/square;x=42;y=0;") match {
            case Root / "board" / BoardExtractor(x, y) if x == "42" && y == "0" => true
            case _ => false
          }) must beTrue
        }

        "a matrix var mid path" in {
          (Path.fromString("/board/square;x=42;y=0/piece") match {
            case Root / "board" / BoardExtractor(x, y) / "piece" if x == "42" && y == "0" => true
            case _ => false
          }) must beTrue
        }

        "too many axes" in {
          (Path.fromString("/board/square;x=42;y=0;z=39") match {
            case Root / "board" / BoardExtractor(x, y) if x == "42" && y == "0" => true
            case _ => false
          }) must beTrue
        }

        "nested extractors" in {
          (Path.fromString("/board/square;x=42;y=0") match {
            case Root / "board" / BoardExtractor(IntVar(x), IntVar(y)) if x == 42 && y == 0 => true
            case _ => false
          }) must beTrue
        }

        "a matrix var with no name" in {
          (Path.fromString("/board/;x=42;y=0") match {
            case Root / "board" / EmptyNameExtractor(x, y) if x == "42" && y == "0" => true
            case _ => false
          }) must beTrue
        }

        "an empty matrix var but why?" in {

          (Path.fromString("/board/square") match {
            case Root / "board" / EmptyExtractor() => true
            case _ => false
          }) must beTrue
        }
      }

      "invalid" >> {
        "empty with semi" in {
          (Path.fromString("/board/square;") match {
            case Root / "board" / BoardExtractor(x @ _, y @ _) => true
            case _ => false
          }) must beFalse
        }

        "empty without semi" in {
          (Path.fromString("/board/square") match {
            case Root / "board" / BoardExtractor(x @ _, y @ _) => true
            case _ => false
          }) must beFalse
        }

        "empty with mismatched name" in {
          (Path.fromString("/board/other") match {
            case Root / "board" / EmptyExtractor() => true
            case _ => false
          }) must beFalse
        }

        "empty axis" in {
          (Path.fromString("/board/square;;y=0") match {
            case Root / "board" / BoardExtractor(x @ _, y @ _) => true
            case _ => false
          }) must beFalse
        }

        "empty too many = in axis" in {
          (Path.fromString("/board/square;x=42=0;y=9") match {
            case Root / "board" / BoardExtractor(x @ _, y @ _) => true
            case _ => false
          }) must beFalse
        }

        "not enough axes" in {
          (Path.fromString("/board/square;x=42") match {
            case Root / "board" / BoardExtractor(x @ _, y @ _) => true
            case _ => false
          }) must beFalse
        }
      }
    }
  }
}
