package org.http4s

import org.scalatest.{Matchers, WordSpec}

/**
 * Created by Bryce Anderson on 4/6/14.
 */
class RequestSpec extends WordSpec with Matchers {

  "Uri" should {
    val q = "param1=3&param2=2&param2=foo"
    val u = Uri(query = Some(q))
    "parse query and represent multiParams as a Map[String,Seq[String]]" in {
      u.multiParams should equal(Map("param1" -> Seq("3"), "param2" -> Seq("2","foo")))
    }

    "parse query and represent params as a Map[String,String] taking the first param" in {
      u.params should equal(Map("param1" -> "3", "param2" -> "2"))
    }

  }

}
