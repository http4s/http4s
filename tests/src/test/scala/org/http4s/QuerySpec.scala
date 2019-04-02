package org.http4s

import org.scalacheck.Prop._

class QuerySpec extends Http4sSpec {

  import Query.KeyValue

  "fromString(query.toString) == query if query.nonEmpty" >> forAll { query: Query =>
    (query.nonEmpty) ==> (Query.fromString(query.toString) == query)
  }

  "Query".can {
    "append a query param" >> forAll { (p: KeyValue, q: Query) =>
      val q2 = q :+ p
      q2 must beAnInstanceOf[Query]
      q2.length must_== q.length + 1
      q2.pairs.last must_== p
      q2.toList must_== (q.toList :+ p)

    }

    "prepend a single Pair element" >> forAll { (q: Query, elem: KeyValue) =>
      val q2 = elem +: q
      q2.length must_== q.length + 1
      q2 must beAnInstanceOf[Query]
      q2.pairs.head must_== elem
      q2.toList must_== (elem :: q.toList)

    }

    "append many KeyValue elements" >> forAll { (q: Query, elems: Seq[KeyValue]) =>
      val q2 = q ++ elems
      q2 must beAnInstanceOf[Query]
      q2.length must_== q.length + elems.length
      q2.toList must_== (q.toList ::: elems.toList)
    }

    "Drop a head element" >> forAll { q: Query =>
      val q2 = q.drop(1)
      q2 must beAnInstanceOf[Query]
      q2.toList must_== q.toList.drop(1)
    }

    "Drop a tail element" >> forAll { q: Query =>
      val q2 = q.dropRight(1)
      q2 must beAnInstanceOf[Query]
      q2.toList must_== q.toList.dropRight(1)
    }

    "Encode special chars in the value" in {
      val u = Query("foo" -> Some(" !$&'()*+,;=:/?@~"), "bar" -> Some("biz"))
      u.renderString must_== "foo=%20%21%24%26%27%28%29%2A%2B%2C%3B%3D%3A/?%40~&bar=biz"
    }

    "Encode special chars in the key" in {
      val u = Query(" !$&'()*+,;=:/?@~" -> Some("foo"), "!" -> None)
      u.renderString must_== "%20%21%24%26%27%28%29%2A%2B%2C%3B%3D%3A/?%40~=foo&%21"
    }
  }
}
