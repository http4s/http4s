package org.http4s

import scalaz.concurrent.Task

/** Provides extension methods for using the a http4s [[org.http4s.client.Client]]
  * {{{
  *   import org.http4s.Status._
  *   import org.http4s.Method._
  *   import org.http4s.EntityDecoder
  *
  *   implicit def client: Client = ???
  *
  *   val r: Task[Result[String]] = GET("https://www.foo.bar/").on(Ok)(EntityDecoder.text)
  *   val req1 = r.run
  *   val req2 = r.run  // Each run is fetches a new result based on the behavior of the Client
  *
  * }}}
  */

package object client {

  import Client.Result

  /** ClientSyntax provides the most convenient way to transform a [[Request]] into a [[Response]]
    *
    * @param request a `Task` that will generate a Request
    */
  implicit class ClientTaskSyntax(request: Task[Request])(implicit client: Client) extends ClientSyntaxBase {
    override protected val resp: Task[Response] = client.prepare(request)
  }

  /** ClientSyntax provides the most convenient way to transform a [[Request]] into a [[Response]]
    *
    * @param request a [[Request]]
    */
  implicit class ClientSyntax(request: Request)(implicit client: Client) extends ClientSyntaxBase {
    override protected val resp: Task[Response] = client.prepare(request)
  }

  /** ClientSyntax provides the most convenient way to transform a [[Request]] into a [[Response]]
    *
    * @param uri a [[Uri]] that will form a GET request
    */
  implicit class ClientUriSyntax(uri: Uri)(implicit client: Client) extends ClientSyntax(Request(uri = uri))
}
