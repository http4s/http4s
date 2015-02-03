package com.example.http4s.blaze



object ClientExample {

  def getSite() = {

    /// code_ref: blaze_client_example

    import org.http4s.Http4s._
    import scalaz.concurrent.Task

    val client = org.http4s.client.blaze.defaultClient

    val page: Task[String] = client(uri("https://www.google.com/")).as[String]

    println(page.run)   // each execution of the Task will refetch the page!
    println(page.run)

    import org.http4s.Status.NotFound
    import org.http4s.Status.ResponseClass.Successful

    // Match on response code!
    val page2 = client(uri("https://twitter.com/doesnt_exist")).flatMap {
      case Successful(resp) => resp.as[String].map("Received response: " + _)
      case NotFound(resp)   => Task.now("Not Found!!!")
      case resp             => Task.now("Failed: " + resp.status)
    }

    println(page2.run)

    /// end_code_ref
  }

  def main(args: Array[String]): Unit = getSite()

}
