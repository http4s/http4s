package org.http4s
package docs

import org.http4s.dsl._

import org.specs2.mutable.Specification
import scalaz.concurrent.Task
import scalaz.stream.Process

class AsynchronousExample extends Specification {
  "Sould compile" in {

    /// code_ref: asynchronous_example
    // Make your model safe and streaming by using a scalaz-stream Process
    def getData(req: Request): Process[Task, String] = ???

    val service = HttpService {
      // Wire your data into your service
      case req@GET -> Root / "streaming" => Ok(getData(req))

      // You can use helpers to send any type of data with an available EntityEncoder[T]
      case GET -> Root / "synchronous" => Ok("This is good to go right now.")
    }
    /// end_code_ref

    ok
  }
}
