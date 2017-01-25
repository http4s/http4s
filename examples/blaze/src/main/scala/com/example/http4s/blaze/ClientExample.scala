// TODO fs2 port
/*
package com.example.http4s.blaze

object ClientExample {

  def getSite() = {

    import org.http4s.Http4s._
    import scalaz.concurrent.Task

    val client = org.http4s.client.blaze.SimpleHttp1Client()

    val page: Task[String] = client.expect[String](uri("https://www.google.com/"))

    for (_ <- 1 to 2)
      println(page.map(_.take(72)).run)   // each execution of the Task will refetch the page!

    // We can do much more: how about decoding some JSON to a scala object
    // after matching based on the response status code?
    import org.http4s.Status.NotFound
    import org.http4s.Status.ResponseClass.Successful
    import io.circe._
    import io.circe.generic.auto._
    import org.http4s.circe.jsonOf

    final case class Foo(bar: String)

    // jsonOf is defined for Json4s, Argonuat, and Circe, just need the right decoder!
    implicit val fooDecoder = jsonOf[Foo]

    // Match on response code!
    val page2 = client.get(uri("http://http4s.org/resources/foo.json")) {
      case Successful(resp) => resp.as[Foo].map("Received response: " + _)
      case NotFound(resp)   => Task.now("Not Found!!!")
      case resp             => Task.now("Failed: " + resp.status)
    }

    println(page2.run)

    client.shutdownNow()
  }

  def main(args: Array[String]): Unit = getSite()

}
*/
