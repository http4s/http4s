package com.example.http4s.blaze


object ClientExample {

  def getSite() = {

/// code_ref: blaze_client_example
    import org.http4s.Http4s._
    import scalaz.concurrent.Task

    val client = org.http4s.client.blaze.defaultClient

    val page: Task[String] = client(uri("https://www.google.com/")).as[String]

    for (_ <- 1 to 2)
      println(page.run.take(72))   // each execution of the Task will refetch the page!

    // We can do much more: how about decoding some JSON to a scala object
    // after matching based on the response status code?
    import org.http4s.Status.NotFound
    import org.http4s.Status.ResponseClass.Successful
    import argonaut.DecodeJson
    import org.http4s.argonaut.jsonOf

    case class Foo(bar: String)

    implicit val fooDecode = DecodeJson(c => for { // Argonaut decoder. Could also use json4s.
      bar <- (c --\ "bar").as[String]
    } yield Foo(bar))

    // jsonOf is defined for Json4s and Argonaut, just need the right decoder!
    implicit val fooDecoder = jsonOf[Foo]

    // Match on response code!
    val page2 = client(uri("http://http4s.org/resources/foo.json")).flatMap {
      case Successful(resp) => resp.as[Foo].map("Received response: " + _)
      case NotFound(resp)   => Task.now("Not Found!!!")
      case resp             => Task.now("Failed: " + resp.status)
    }

    println(page2.run)
/// end_code_ref
  }

  def main(args: Array[String]): Unit = getSite()

}
