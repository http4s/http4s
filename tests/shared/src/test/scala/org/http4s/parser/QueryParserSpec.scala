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
package parser

import org.http4s.internal.CollectionCompat

import java.nio.CharBuffer
import scala.io.Codec

class QueryParserSpec extends Http4sSuite {
  def parseQueryString(str: String): ParseResult[Query] =
    QueryParser.parseQueryString(str)

  test("The QueryParser should correctly extract complete key value pairs") {
    assertEquals(parseQueryString("key=value"), Right(Query("key" -> Some("value"))))
    assertEquals(
      parseQueryString("key=value&key2=value2"),
      Right(Query("key" -> Some("value"), "key2" -> Some("value2"))),
    )
  }

  test("The QueryParser should decode URL-encoded keys and values") {
    assertEquals(parseQueryString("ke%25y=value"), Right(Query("ke%y" -> Some("value"))))
    assertEquals(
      parseQueryString("key=value%26&key2=value2"),
      Right(Query("key" -> Some("value&"), "key2" -> Some("value2"))),
    )
  }

  test("The QueryParser should return an empty Map for an empty query string") {
    assertEquals(parseQueryString(""), Right(Query.empty))
  }

  test(
    "The QueryParser should return an empty value for keys without a value following the '=' and keys without following '='"
  ) {
    assertEquals(parseQueryString("key=&key2"), Right(Query("key" -> Some(""), "key2" -> None)))
  }

  test("The QueryParser should accept empty key value pairs") {
    assertEquals(
      parseQueryString("&&b&"),
      Right(Query("" -> None, "" -> None, "b" -> None, "" -> None)),
    )
  }

  test("The QueryParser should Handle '=' in a query string") {
    assertEquals(parseQueryString("a=b=c"), Right(Query("a" -> Some("b=c"))))
  }

//    test("The QueryParser should Gracefully handle invalid URL encoding") {
//      assertEquals(parseQueryString("a=b%G"), Right(Query("a" -> Some("b%G"))))
//    }

  test("The QueryParser should Allow ';' seperators") {
    assertEquals(parseQueryString("a=b;c"), Right(Query("a" -> Some("b"), "c" -> None)))
  }

  test("The QueryParser should Allow PHP-style [] in keys") {
    assertEquals(
      parseQueryString("a[]=b&a[]=c"),
      Right(Query("a[]" -> Some("b"), "a[]" -> Some("c"))),
    )
  }

  test("The QueryParser should QueryParser using QChars doesn't allow PHP-style [] in keys") {
    val queryString = "a[]=b&a[]=c"
    assert(
      new QueryParser(Codec.UTF8, colonSeparators = true, qChars = QueryParser.QChars)
        .decode(CharBuffer.wrap(queryString), flush = true)
        .isLeft
    )
  }

  test("The QueryParser should Reject a query with invalid char") {
    assert(parseQueryString("獾").isLeft)
    assert(parseQueryString("foo獾bar").isLeft)
    assert(parseQueryString("foo=獾").isLeft)
  }

  test("The QueryParser should Keep CharBuffer position if not flushing") {
    val s = "key=value&stuff=cat"
    val cs = CharBuffer.wrap(s)
    val r = new QueryParser(Codec.UTF8, colonSeparators = true).decode(cs, flush = false)

    assertEquals(r, Right(Query("key" -> Some("value"))))
    assertEquals(cs.remaining, 9)

    val r2 = new QueryParser(Codec.UTF8, colonSeparators = true).decode(cs, flush = false)
    assertEquals(r2, Right(Query()))
    assertEquals(cs.remaining(), 9)

    val r3 = new QueryParser(Codec.UTF8, colonSeparators = true).decode(cs, flush = true)
    assertEquals(r3, Right(Query("stuff" -> Some("cat"))))
    assertEquals(cs.remaining(), 0)
  }

  test("The QueryParser should be stack safe") {
    val value = CollectionCompat.LazyList.continually('X').take(1000000).mkString
    val query = s"little=x&big=${value}"
    assertEquals(parseQueryString(query), Right(Query("little" -> Some("x"), "big" -> Some(value))))
  }

}
