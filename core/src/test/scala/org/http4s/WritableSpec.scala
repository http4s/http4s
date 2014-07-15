package org.http4s

import scala.language.postfixOps
import scalaz.concurrent.Task

// the http4s team resents importing this.

import org.http4s.Status._
import concurrent.Future
import org.scalatest.{Matchers, WordSpec}

class WritableSpec extends WordSpec with Matchers {
  "Writable" should {
    import Writable._

    def route(in: Task[Response]) = {
      new String(in.run.body.runLog.run.reduce(_ ++ _).toArray)
    }


    "Get Strings" in {
      route {Ok("pong")} should equal("pong")
    }

    "Get Sequences of Strings" in {
      val result = (0 until 1000).map{ i => s"This is string number $i" }
      route{Ok(result)} should equal (result.foldLeft ("")(_ + _))
    }

    "Get Sequences of Ints" in {
      val input = (0 until 10)
      route{Ok(input)} should equal (input.foldLeft ("")(_ + _))
    }

    "Get Integers" in {
      route{Ok(1)} should equal ("1")
    }

    "Get Html" in {
      val myxml = <html><body>Hello</body></html>
      route { Ok(myxml) } should equal (myxml.buildString(false))
    }

    "Get Array[Byte]" in {
      val hello = "hello"
      route { Ok(hello.getBytes) } should equal(hello)
    }

    "Get Futures" in {
      import concurrent.ExecutionContext.Implicits.global
      val txt = "Hello"
      route(Ok(Future(txt))) should equal (txt)
    }

    "Get Tasks" in {
      val txt = "Hello"
      route(Ok(Task(txt))) should equal (txt)
    }

    "Get Processes" in {
      import scalaz.stream.Process.emit
      val txt = "Hello"
      route(Ok(emit(txt))) should equal (txt)
    }

  }
}

