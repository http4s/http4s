package org.http4s

import org.scalacheck.Prop._

class QuerySpec extends Http4sSpec {

  import FormQuery.KeyValue

  "fromString(query.toString) == query if query.nonEmpty" >> forAll { query: FormQuery =>
    (query.nonEmpty) ==> (FormQuery.fromString(query.encoded) == query)
  }

  "Query Builder" can {
    "build a query from a FormQuery" >> forAll { query: FormQuery =>
      val b = FormQuery.newBuilder
      query.foreach(b.+=)
      b.result() must_== query
    }

    "build a query from a Seq" >> forAll { elems: Seq[KeyValue] =>
      val q = (FormQuery.newBuilder ++= elems).result()
      q must beAnInstanceOf[FormQuery]
      q.toSeq must_== elems
    }
  }

  "FormQuery" can {
    val elem = ("k", Some("v"))

    "append a query param" >> forAll { (p: KeyValue, q: FormQuery) =>
      val q2 = q :+ p
      q2 must beAnInstanceOf[FormQuery]
      q2.length must_== q.length + 1
      q2.last must_== p
      q2.toList must_== (q.toList :+ p)

    }

    "append a single other element" >> forAll { (s: String, q: FormQuery) =>
      val q2 = q :+ s
      q2.isInstanceOf[FormQuery] must_== false
      q2.length must_== q.length + 1
      q2.last must_== s
      q2.toList must_==(q.toList :+ s)
    }

    "prepend a single Pair element" >> forAll { (q: FormQuery, elem: KeyValue) =>
      val q2 = elem +: q
      q2.length must_== q.length + 1
      q2 must beAnInstanceOf[FormQuery]
      q2.head must_== elem
      q2.toList must_==(elem::q.toList)

    }

    "prepend a single other element" >> forAll { (q: FormQuery, elem: String) =>
      val q2 = elem +: q
      q2.length must_== q.length + 1
      q2.head must_== elem
      q2.isInstanceOf[FormQuery] must_== false
      q2.toList must_==(elem::q.toList)
    }

    "append many KeyValue elements" >> forAll { (q: FormQuery, elems: Seq[KeyValue]) =>
      val q2 = q ++ elems
      q2 must beAnInstanceOf[FormQuery]
      q2.length must_== q.length + elems.length
      q2.toList must_== (q.toList ::: elems.toList)
    }

    "append many other elements" >> forAll { (q: FormQuery, elems: Seq[String]) =>
      val q2 = q ++ elems
      q2.isInstanceOf[FormQuery] must_== false
      q2.length must_== q.length + elems.length
      q2.toList must_== (q.toList ::: elems.toList)
    }

    "Drop a head element" >> forAll { q: FormQuery =>
      val q2 = q.drop(1)
      q2 must beAnInstanceOf[FormQuery]
      q2.toList must_== q.toList.drop(1)
    }

    "Drop a tail element" >> forAll { q: FormQuery =>
      val q2 = q.dropRight(1)
      q2 must beAnInstanceOf[FormQuery]
      q2.toList must_== q.toList.dropRight(1)
    }

    "get a tail for non-empty FormQuery" >> forAll { q: FormQuery =>
      (q.nonEmpty) ==> (q.tail.toList == q.toList.tail)
    }

    "Encode special chars in the value" in {
      val u = FormQuery("foo" -> Some(" !$&'()*+,;=:/?@~"), "bar" -> Some("biz"))
      u.encoded must_== "foo=+%21%24%26%27%28%29*%2B%2C%3B%3D%3A%2F%3F%40%7E&bar=biz"
    }

    "Encode special chars in the key" in {
      val u = FormQuery(" !$&'()*+,;=:/?@~" -> Some("foo"), "!" -> None)
      u.encoded must_== "+%21%24%26%27%28%29*%2B%2C%3B%3D%3A%2F%3F%40%7E=foo&%21"
    }
  }
}
