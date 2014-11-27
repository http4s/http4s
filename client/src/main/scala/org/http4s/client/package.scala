package org.http4s

import org.http4s.client.Client.BadResponse
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

  implicit class ClientSyntax(request: Request)(implicit client: Client) extends ClientSyntaxBase {
    override protected val resp: Task[Response] = client.prepare(request)
  }



  trait ClientSyntaxBase {
    /** Generate a Task which, when executed, will perform the request using the provided client */
    protected def resp: Task[Response]

    /** Generate a Task which, when executed, will perform the request and if the response
      * is of type `status`, decodes it.
      */
    def on[T](status: Status)(decoder: EntityDecoder[T])(implicit client: Client): Task[Result[T]] =
      Client.decode(resp){
        case Response(s,_,_,_,_) if s == status => decoder
        case Response(s,_,_,b,_)                => EntityDecoder.error(BadResponse(s, ""))
      }

    /** Generate a Task which, when executed, will perform the request and attempt to decode it */
    def decode[T](f: Response => EntityDecoder[T]): Task[Result[T]] =
      Client.decode(resp)(f)
  }
}
