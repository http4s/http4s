package org.http4s

import org.specs2.mutable.Specification

import Query.KV

class QuerySpec extends Specification {

  "Query" can {
    val elem = KV("k", Some("v"))

    "append a single Pair element" in {
      val q = Query.empty :+ elem
      q must beAnInstanceOf[Query]
      q.length must_== 1
      q.head must_== elem
    }

    "append a single other element" in {
      val q = Query.empty :+ "cat"
      q.length must_== 1
      q.head must_== "cat"
      q.isInstanceOf[Query] must_== false
    }

    "prepend a single Pair element" in {
      val q = elem +: Query.empty
      q must beAnInstanceOf[Query]
      q.length must_== 1
      q.head must_== elem
    }

    "prepend a single other element" in {
      val q = "cat" +: Query.empty
      q.length must_== 1
      q.head must_== "cat"
      q.isInstanceOf[Query] must_== false
    }

    "append many Pair elements" in {
      val q = Query.empty ++ Seq(elem, elem)
      q must beAnInstanceOf[Query]
      q.length must_== 2
      q must_== Seq(elem, elem)
    }

    "append many other elements" in {
      val things = Seq("cat", "dog")
      val q = Query.empty ++ things
      q.isInstanceOf[Query] must_== false
      q.length must_== 2
      q must_== things
    }
  }

}
