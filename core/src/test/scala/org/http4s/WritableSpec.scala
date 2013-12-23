
package org.http4s

import scala.language.postfixOps
import scalaz.concurrent.Task

// the http4s team resents importing this.

import org.http4s.Status._
import concurrent.Future
import org.scalatest.{Matchers, WordSpec}

/**
 * @author Bryce Anderson
 * Created on 3/9/13 at 6:15 PM
 */
class WritableSpec extends WordSpec with Matchers {
  "Writable" should {
    import Writable._

    def route(in: HttpService) = {
      val server = new MockServer(in)
      new String(server.apply(Request()).run.body)
    }


    "Get Strings" in {
      route { case _ => Ok("pong")} should equal("pong")
    }

    "Get Sequences of Strings" in {
      val result = (0 until 1000).map{ i => s"This is string number $i" }
      route{ case _ => Ok(result)} should equal (result.foldLeft ("")(_ + _))
    }

    "Get Sequences of Ints" in {
      val input = (0 until 10)
      route{ case _ => Ok(input)} should equal (input.foldLeft ("")(_ + _))
    }

    "Get Integers" in {
      route{ case _ => Ok(1)} should equal ("1")
    }

    "Get Html" in {
      val myxml = <html><body>Hello</body></html>
      route { case _ => Ok(myxml) } should equal (myxml.buildString(false))
    }

    "Get Array[Byte]" in {
      val hello = "hello"
      route { case _ => Ok(hello.getBytes) } should equal(hello)
    }

    "Get Futures" in {
      import concurrent.ExecutionContext.Implicits.global
      val txt = "Hello"
      val rt: HttpService = {
        case _ => Ok(Future(txt))
      }
      route(rt) should equal (txt)
    }

    "Get Tasks" in {
      val txt = "Hello"
      val rt: HttpService = {
        case _ => Ok(Task(txt))
      }
      route(rt) should equal (txt)
    }

    "Get Processes" in {
      import scalaz.stream.Process.emit
      val txt = "Hello"
      val rt: HttpService = {
        case _ => Ok(emit(txt))
      }
      route(rt) should equal (txt)
    }

  }
}

