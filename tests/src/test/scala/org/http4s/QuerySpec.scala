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

import cats.kernel.laws.discipline._
import org.http4s.laws.discipline.ArbitraryInstances.http4sTestingCogenForQuery

import org.http4s.laws.discipline.arbitrary._
import org.scalacheck.Prop._

class QuerySpec extends Http4sSuite {
  import Query.KeyValue

  test("fromString(query.toString) == query if query.nonEmpty") {
    forAll { (query: Query) =>
      (query.nonEmpty) ==> (Query.fromString(query.toString) == query)
    }
  }

  {
    test("Query can append a query param") {
      forAll { (p: KeyValue, q: Query) =>
        val q2 = q :+ p
        q2.isInstanceOf[Query] &&
        q2.length == q.length + 1 &&
        q2.pairs.last == p &&
        q2.toList == (q.toList :+ p)
      }
    }

    test("Query can prepend a single Pair element") {
      forAll { (q: Query, elem: KeyValue) =>
        val q2 = elem +: q
        q2.length == q.length + 1 &&
        q2.isInstanceOf[Query] &&
        q2.pairs.head == elem &&
        q2.toList == (elem :: q.toList)
      }
    }

    test("Query can append many KeyValue elements") {
      forAll { (q: Query, elems: Seq[KeyValue]) =>
        val q2 = q ++ elems
        q2.isInstanceOf[Query] &&
        q2.length == q.length + elems.length &&
        q2.toList == (q.toList ::: elems.toList)
      }
    }

    test("Query can Drop a head element") {
      forAll { (q: Query) =>
        val q2 = q.drop(1)
        q2.isInstanceOf[Query] &&
        q2.toList == q.toList.drop(1)
      }
    }

    test("Query can Drop a tail element") {
      forAll { (q: Query) =>
        val q2 = q.dropRight(1)
        q2.isInstanceOf[Query] &&
        q2.toList == q.toList.dropRight(1)
      }
    }

    test("Query can Encode special chars in the value") {
      val u = Query("foo" -> Some(" !$&'()*+,;=:/?@~"), "bar" -> Some("biz"))
      assertEquals(u.renderString, "foo=%20%21%24%26%27%28%29%2A%2B%2C%3B%3D%3A/?%40~&bar=biz")
    }

    test("Query can Encode special chars in the key") {
      val u = Query(" !$&'()*+,;=:/?@~" -> Some("foo"), "!" -> None)
      assertEquals(u.renderString, "%20%21%24%26%27%28%29%2A%2B%2C%3B%3D%3A/?%40~=foo&%21")
    }
  }

  checkAll("Order[Query]", OrderTests[Query].order)
  checkAll("Hash[Query]", HashTests[Query].hash)

}
