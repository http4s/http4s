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
import org.http4s.dsl.io._
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary

class PathSpec extends Http4sSpec {
  implicit val arbitraryPath: Arbitrary[Path] =
    Arbitrary {
      arbitrary[List[String]].map(Path(_))
    }

  "Path" should {
    "/foo/bar" in {
      Path("/foo/bar") must_== Path("foo", "bar")
    }

    "foo/bar" in {
      Path("foo/bar") must_== Path("foo", "bar")
    }

    "//foo/bar" in {
      Path("//foo/bar") must_== Path("", "foo", "bar")
    }

    "~ extractor on Path" in {
      (Path("/foo.json") match {
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

    "Root extractor" in {
      (Path("/") match {
        case Root => true
        case _ => false
      }) must beTrue
    }

    "Root extractor, no partial match" in {
      (Path("/test.json") match {
        case Root => true
        case _ => false
      }) must beFalse
    }

    "Root extractor, empty path" in {
      (Path("") match {
        case Root => true
        case _ => false
      }) must beTrue
    }

    "/ extractor" in {
      (Path("/1/2/3/test.json") match {
        case Root / "1" / "2" / "3" / "test.json" => true
        case _ => false
      }) must beTrue
    }

    "/: extractor" in {
      (Path("/1/2/3/test.json") match {
        case "1" /: "2" /: path => Some(path)
        case _ => None
      }) must_== Some(Path("/3/test.json"))
    }

    "/: should not crash without trailing slash" in {
      // Bug reported on Gitter
      Path("/cameras/1NJDOI") match {
        case "cameras" /: _ /: "events" /: _ /: "exports" /: _ => false
        case _ => true
      }
    }

    "trailing slash" in {
      (Path("/1/2/3/") match {
        case Root / "1" / "2" / "3" / "" => true
        case _ => false
      }) must beTrue
    }

    "encoded chars" in {
      (Path("/foo%20bar/and%2For/1%2F2") match {
        case Root / "foo bar" / "and/or" / "1/2" => true
        case _ => false
      }) must beTrue
    }

    "encode chars in toString" in {
      (Root / "foo bar" / "and/or" / "1/2").toString must_==
        "/foo%20bar/and%2For/1%2F2"
    }

    "Int extractor" in {
      (Path("/user/123") match {
        case Root / "user" / IntVar(userId) => userId == 123
        case _ => false
      }) must beTrue
    }

    "Int extractor, invalid int" in {
      (Path("/user/invalid") match {
        case Root / "user" / IntVar(userId @ _) => true
        case _ => false
      }) must beFalse
    }

    "Int extractor, number format error" in {
      (Path("/user/2147483648") match {
        case Root / "user" / IntVar(userId @ _) => true
        case _ => false
      }) must beFalse
    }

    "Long extractor" >> {
      "valid" >> {
        "small positive number" in {
          (Path("/user/123") match {
            case Root / "user" / LongVar(userId) => userId == 123
            case _ => false
          }) must beTrue
        }
        "negative number" in {
          (Path("/user/-432") match {
            case Root / "user" / LongVar(userId) => userId == -432
            case _ => false
          }) must beTrue
        }
      }
      "invalid" >> {
        "a word" in {
          (Path("/user/invalid") match {
            case Root / "user" / LongVar(userId @ _) => true
            case _ => false
          }) must beFalse
        }
        "number but out of domain" in {
          (Path("/user/9223372036854775808") match {
            case Root / "user" / LongVar(userId @ _) => true
            case _ => false
          }) must beFalse
        }
      }
    }

    "UUID extractor" >> {
      "valid" >> {
        "a UUID" in {
          (Path("/user/13251d88-7a73-4fcf-b935-54dfae9f023e") match {
            case Root / "user" / UUIDVar(userId) =>
              userId.toString == "13251d88-7a73-4fcf-b935-54dfae9f023e"
            case _ => false
          }) must beTrue
        }
      }
      "invalid" >> {
        "a number" in {
          (Path("/user/123") match {
            case Root / "user" / UUIDVar(userId @ _) => true
            case _ => false
          }) must beFalse
        }
        "a word" in {
          (Path("/user/invalid") match {
            case Root / "user" / UUIDVar(userId @ _) => true
            case _ => false
          }) must beFalse
        }
        "a bad UUID" in {
          (Path("/user/13251d88-7a73-4fcf-b935") match {
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
          (Path("/board/square;x=42;y=0") match {
            case Root / "board" / BoardExtractor(x, y) if x == "42" && y == "0" => true
            case _ => false
          }) must beTrue
        }

        "a matrix var with empty axis segment" in {
          (Path("/board/square;x=42;;y=0") match {
            case Root / "board" / BoardExtractor(x, y) if x == "42" && y == "0" => true
            case _ => false
          }) must beTrue
        }

        "a matrix var with empty trailing axis segment" in {
          (Path("/board/square;x=42;y=0;") match {
            case Root / "board" / BoardExtractor(x, y) if x == "42" && y == "0" => true
            case _ => false
          }) must beTrue
        }

        "a matrix var mid path" in {
          (Path("/board/square;x=42;y=0/piece") match {
            case Root / "board" / BoardExtractor(x, y) / "piece" if x == "42" && y == "0" => true
            case _ => false
          }) must beTrue
        }

        "too many axes" in {
          (Path("/board/square;x=42;y=0;z=39") match {
            case Root / "board" / BoardExtractor(x, y) if x == "42" && y == "0" => true
            case _ => false
          }) must beTrue
        }

        "nested extractors" in {
          (Path("/board/square;x=42;y=0") match {
            case Root / "board" / BoardExtractor(IntVar(x), IntVar(y)) if x == 42 && y == 0 => true
            case _ => false
          }) must beTrue
        }

        "a matrix var with no name" in {
          (Path("/board/;x=42;y=0") match {
            case Root / "board" / EmptyNameExtractor(x, y) if x == "42" && y == "0" => true
            case _ => false
          }) must beTrue
        }

        "an empty matrix var but why?" in {

          (Path("/board/square") match {
            case Root / "board" / EmptyExtractor() => true
            case _ => false
          }) must beTrue
        }
      }

      "invalid" >> {
        "empty with semi" in {
          (Path("/board/square;") match {
            case Root / "board" / BoardExtractor(x @ _, y @ _) => true
            case _ => false
          }) must beFalse
        }

        "empty without semi" in {
          (Path("/board/square") match {
            case Root / "board" / BoardExtractor(x @ _, y @ _) => true
            case _ => false
          }) must beFalse
        }

        "empty with mismatched name" in {
          (Path("/board/other") match {
            case Root / "board" / EmptyExtractor() => true
            case _ => false
          }) must beFalse
        }

        "empty axis" in {
          (Path("/board/square;;y=0") match {
            case Root / "board" / BoardExtractor(x @ _, y @ _) => true
            case _ => false
          }) must beFalse
        }

        "empty too many = in axis" in {
          (Path("/board/square;x=42=0;y=9") match {
            case Root / "board" / BoardExtractor(x @ _, y @ _) => true
            case _ => false
          }) must beFalse
        }

        "not enough axes" in {
          (Path("/board/square;x=42") match {
            case Root / "board" / BoardExtractor(x @ _, y @ _) => true
            case _ => false
          }) must beFalse
        }
      }
    }

    "consistent apply / toList" in prop { (p: Path) =>
      Path(p.toList) must_== p
    }

    "Path.apply is stack safe" in {
      Path("/" * 1000000) must beAnInstanceOf[Path]
    }
  }
}
