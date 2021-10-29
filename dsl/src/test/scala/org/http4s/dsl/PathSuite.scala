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

package org.http4s
package dsl

import cats.effect.IO
import org.http4s.Uri.Path
import org.http4s.Uri.Path.Root
import org.http4s.Uri.Path.Segment
import org.http4s.dsl.io._
import org.http4s.syntax.AllSyntax
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen

class PathSuite extends Http4sSuite with AllSyntax {
  implicit val arbitraryPath: Gen[Path] =
    arbitrary[List[String]]
      .map(_.foldLeft(Path.Root)((acc, e) => acc / Segment(e)))

  test("Path should ~ extractor on Path without Root") {
    assert(Path.unsafeFromString("foo.json") match {
      case Path.empty / "foo" ~ "json" => true
      case _ => false
    })
  }

  test("Path should ~ extractor on Path with Root") {
    assert(Path.unsafeFromString("/foo.json") match {
      case Root / "foo" ~ "json" => true
      case _ => false
    })
  }

  test("Path should ~ extractor on filename foo.json") {
    assert("foo.json" match {
      case "foo" ~ "json" => true
      case _ => false
    })
  }

  test("Path should ~ extractor on filename foo") {
    assert("foo" match {
      case "foo" ~ "" => true
      case _ => false
    })
  }

  test("Path should -> extractor /test.json") {
    val req = Request[IO](method = Method.GET, uri = uri"/test.json")
    assert(req match {
      case GET -> Root / "test.json" => true
      case _ => false
    })
  }

  test("Path should -> extractor /foo/test.json") {
    val req = Request[IO](method = Method.GET, uri = uri"/foo/test.json")
    assert(req match {
      case GET -> Root / "foo" / "test.json" => true
      case _ => false
    })
  }

  test("Path should → extractor /test.json") {
    val req = Request[IO](method = Method.GET, uri = uri"/test.json")
    assert(req match {
      case GET → (Root / "test.json") => true
      case _ => false
    })
  }

  test("Path should request path info extractor for /") {
    val req = Request[IO](method = Method.GET, uri = uri"/")
    assert(req match {
      case _ -> Root => true
      case _ => false
    })
  }

  test("Path should / extractor") {
    assert(Path.unsafeFromString("/1/2/3/test.json") match {
      case Root / "1" / "2" / "3" / "test.json" => true
      case _ => false
    })
  }

  test("Path should /: extractor") {
    assert((Path.unsafeFromString("/1/2/3/test.json") match {
      case "1" /: "2" /: path => Some(path)
      case _ => None
    }).contains(Path.unsafeFromString("3/test.json")))
  }

  test("Path should /: should not crash without trailing slash") {
    // Bug reported on Gitter
    assert(Path.unsafeFromString("/cameras/1NJDOI") match {
      case "cameras" /: _ /: "events" /: _ /: "exports" /: _ => false
      case _ => true
    })
  }

  test("Path should trailing slash") {
    assert(Path.unsafeFromString("/1/2/3/") match {
      case Root / "1" / "2" / "3" / "" => true
      case _ => false
    })
  }

  test("Path should encoded chars") {
    assert(Path.unsafeFromString("/foo%20bar/and%2For/1%2F2") match {
      case Root / "foo bar" / "and/or" / "1/2" => true
      case _ => false
    })
  }

  test("Path should Int extractor") {
    assert(Path.unsafeFromString("/user/123") match {
      case Root / "user" / IntVar(userId) => userId == 123
      case _ => false
    })
  }

  test("Path should Int extractor, invalid int") {
    assert(!(Path.unsafeFromString("/user/invalid") match {
      case Root / "user" / IntVar(userId @ _) => true
      case _ => false
    }))
  }

  test("Path should Int extractor, number format error") {
    assert(!(Path.unsafeFromString("/user/2147483648") match {
      case Root / "user" / IntVar(userId @ _) => true
      case _ => false
    }))
  }

  test("Long extractor should valid small positive number") {
    assert(Path.unsafeFromString("/user/123") match {
      case Root / "user" / LongVar(userId) => userId == 123
      case _ => false
    })
  }

  test("Long extractor should valid negative number") {
    assert(Path.unsafeFromString("/user/-432") match {
      case Root / "user" / LongVar(userId) => userId == -432
      case _ => false
    })
  }

  test("Long extractor invalid a word") {
    assert(!(Path.unsafeFromString("/user/invalid") match {
      case Root / "user" / LongVar(userId @ _) => true
      case _ => false
    }))
  }

  test("Long extractor invalid number but out of domain") {
    assert(!(Path.unsafeFromString("/user/9223372036854775808") match {
      case Root / "user" / LongVar(userId @ _) => true
      case _ => false
    }))
  }

  test("UUID extractor valid a UUID") {
    assert(Path.unsafeFromString("/user/13251d88-7a73-4fcf-b935-54dfae9f023e") match {
      case Root / "user" / UUIDVar(userId) =>
        userId.toString == "13251d88-7a73-4fcf-b935-54dfae9f023e"
      case _ => false
    })
  }

  test("UUID extractor invalid a number") {
    assert(!(Path.unsafeFromString("/user/123") match {
      case Root / "user" / UUIDVar(userId @ _) => true
      case _ => false
    }))
  }

  test("UUID extractor invalid a word") {
    assert(!(Path.unsafeFromString("/user/invalid") match {
      case Root / "user" / UUIDVar(userId @ _) => true
      case _ => false
    }))
  }

  test("UUID extractor invalid a bad UUID") {
    assert(!(Path.unsafeFromString("/user/13251d88-7a73-4fcf-b935") match {
      case Root / "user" / UUIDVar(userId @ _) => true
      case _ => false
    }))
  }

  object BoardExtractor extends impl.MatrixVar("square", List("x", "y"))

  object EmptyNameExtractor extends impl.MatrixVar("", List("x", "y"))

  object EmptyExtractor extends impl.MatrixVar("square", List.empty[String])

  test("Matrix extractor valid a matrix var") {
    assert(Path.unsafeFromString("/board/square;x=42;y=0") match {
      case Root / "board" / BoardExtractor(x, y) if x == "42" && y == "0" => true
      case _ => false
    })
  }

  test("Matrix extractor valid a matrix var with empty axis segment") {
    assert(Path.unsafeFromString("/board/square;x=42;;y=0") match {
      case Root / "board" / BoardExtractor(x, y) if x == "42" && y == "0" => true
      case _ => false
    })
  }

  test("Matrix extractor valid a matrix var with empty trailing axis segment") {
    assert(Path.unsafeFromString("/board/square;x=42;y=0;") match {
      case Root / "board" / BoardExtractor(x, y) if x == "42" && y == "0" => true
      case _ => false
    })
  }

  test("Matrix extractor valid a matrix var mid path") {
    assert(Path.unsafeFromString("/board/square;x=42;y=0/piece") match {
      case Root / "board" / BoardExtractor(x, y) / "piece" if x == "42" && y == "0" => true
      case _ => false
    })
  }

  test("Matrix extractor valid too many axes") {
    assert(Path.unsafeFromString("/board/square;x=42;y=0;z=39") match {
      case Root / "board" / BoardExtractor(x, y) if x == "42" && y == "0" => true
      case _ => false
    })
  }

  test("Matrix extractor valid nested extractors") {
    assert(Path.unsafeFromString("/board/square;x=42;y=0") match {
      case Root / "board" / BoardExtractor(IntVar(x), IntVar(y)) if x == 42 && y == 0 => true
      case _ => false
    })
  }

  test("Matrix extractor valid a matrix var with no name") {
    assert(Path.unsafeFromString("/board/;x=42;y=0") match {
      case Root / "board" / EmptyNameExtractor(x, y) if x == "42" && y == "0" => true
      case _ => false
    })
  }

  test("Matrix extractor valid an empty matrix var but why?") {

    assert(Path.unsafeFromString("/board/square") match {
      case Root / "board" / EmptyExtractor() => true
      case _ => false
    })
  }

  test("Matrix extractor invalid empty with semi") {
    assert(!(Path.unsafeFromString("/board/square;") match {
      case Root / "board" / BoardExtractor(x @ _, y @ _) => true
      case _ => false
    }))
  }

  test("Matrix extractor invalid empty without semi") {
    assert(!(Path.unsafeFromString("/board/square") match {
      case Root / "board" / BoardExtractor(x @ _, y @ _) => true
      case _ => false
    }))
  }

  test("Matrix extractor invalid empty with mismatched name") {
    assert(!(Path.unsafeFromString("/board/other") match {
      case Root / "board" / EmptyExtractor() => true
      case _ => false
    }))
  }

  test("Matrix extractor invalid empty axis") {
    assert(!(Path.unsafeFromString("/board/square;;y=0") match {
      case Root / "board" / BoardExtractor(x @ _, y @ _) => true
      case _ => false
    }))
  }

  test("Matrix extractor invalid empty too many = in axis") {
    assert(!(Path.unsafeFromString("/board/square;x=42=0;y=9") match {
      case Root / "board" / BoardExtractor(x @ _, y @ _) => true
      case _ => false
    }))
  }

  test("Matrix extractor invalid not enough axes") {
    assert(!(Path.unsafeFromString("/board/square;x=42") match {
      case Root / "board" / BoardExtractor(x @ _, y @ _) => true
      case _ => false
    }))
  }
}
