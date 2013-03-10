package org.http4s

import org.specs2.mutable.Specification
import play.api.libs.iteratee.Enumerator
import org.http4s.Status._
import akka.util.ByteString
import concurrent.Future

/**
 * @author Bryce Anderson
 * Created on 3/9/13 at 6:15 PM
 */
class WritableSpec extends Specification {
  "Writable" should {
    import Writable._

    def route(in: Route): String =
      new String(new MockServer(in).response(RequestPrelude(), Enumerator.eof[HttpChunk]).body)

    "Build String"

    "Get Strings" in {
      route { case _ => Ok("pong")} must_== "pong"
    }

    "Get Sequences of Strings" in {
      val result = (0 until 1000).map{ i => s"This is string number $i" }
      route{ case _ => Ok(result)} must_==
        (result.foldLeft ("")(_ + _))
    }

    "Get Sequences of Ints" in {
      val input = (0 until 10)
      route{ case _ => Ok(input)} must_==
        (input.foldLeft ("")(_ + _))
    }

    "Get Integers" in {
      route{ case _ => Ok(1)} must_== "1"
    }

    "Get ByteStrings" in {
      val str = "Hello"
      route{ case _ => Ok(ByteString(str.getBytes))} must_== str
    }

    "Get Html" in {
      val myxml = <html><body>Hello</body></html>
      route { case _ => Ok(myxml) } must_== myxml.buildString(false)
    }

    "Get Futures" in {
      import concurrent.ExecutionContext.Implicits.global
      val txt = "Hello"
      val rt: Route = {
        case _ => Ok(Future(txt))
      }
      route(rt) must_== txt
    }

  }
}
