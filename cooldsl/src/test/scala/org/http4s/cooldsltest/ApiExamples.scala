package org.http4s.cooldsltest

import org.specs2.mutable.Specification
import org.http4s.{Header, Method, Status}
import Status.Ok
import org.http4s.cooldsl._

/**
 * Created by Bryce Anderson on 4/29/14.
 */

class ApiExamples extends Specification {

  "mock api" should {
    "Make it easy to compose routes" in {

      // the path can be built up in multiple steps and the parts reused
      val path = Method.Post / "hello"
      val path2 = path / 'world -? query[Int]("fav") // the symbol 'world just says 'capture a String'
      path ==> { () => Ok("Empty")}
      path2 ==> { (world: String, fav: Int) => Ok(s"Received $fav, $world")}

      // It can also be made all at once
      val path3 = Method.Post / "hello" / parse[Int] -? query[Int]("fav")
      path3 ==> {(i1: Int, i2: Int) => Ok(s"Sum of the number is ${i1+i2}")}

      // You can automatically parse variables in the path
      val path4 = Method.Get / "helloworldnumber" / parse[Int] / "foo"
      path4 ==> {i: Int => Ok("Received $i")}

      // You can capture the entire rest of the tail using -*
      val path5 = Method.Get / "hello" / -* ==>{ r: List[String] => Ok(s"Got the rest: ${r.mkString}")}

      // header validation is also composable
      val v1 = requireThat(Header.`Content-Length`)(_.length > 0)
      val v2 = v1 && capture(Header.ETag)

      // Now these two can be combined to make the 'Router'
      val r = path2.validate(v2)

      // you can continue to add validation actions to a 'Router' but can no longer modify the path
      val r2 = r >>> require(Header.`Cache-Control`)
      // r2 / "stuff" // Doesn't work

      // Now this can be combined with a method to make the 'Action'
      val action = r2 ==> {(world: String, fav: Int, tag: Header.ETag) =>
        Ok("Success").withHeaders(Header.ETag(fav.toString))
      }

      /** Boolean logic
        * Just as you can perform 'and' operations which have the effect of building up a path or
        * making mutually required header validations, you can perform 'or' logic with your routes
        */

      val path6 = "one" / parse[Int]
      val path7 = "two" / parse[Int]

      val v6 = requireMap(Header.`Content-Length`)(_.length)
      val v7 = requireMap(Header.ETag)(_ => -1)

      Method.Get / (path6 || path7) -? query[String]("foo") >>> (v6 || v7) ==> { (i: Int, foo: String, v: Int) =>
        Ok(s"Received $i, $foo, $v")
      }

      true should_== true
    }

  }

}
