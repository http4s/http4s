package org.http4s

import org.http4s.client.Client.BadResponse
import scalaz.concurrent.Task

/** Provides extension methods for using the http4s [[org.http4s.client.Client]] */
package object client {

  import Client.Result

  /** ClientSyntax provides the most convenient way to transform a [[Request]] into a [[Response]]
    *
    * @param request a `Task` that will generate a Request
    */
  implicit class ClientSyntax(request: Task[Request]) {

    /** Generate a Task which, when executed, will perform the request using the provided client */
    def build(implicit client: Client): Task[Response] = client.prepare(request)

    /** Generate a Task which, when executed, will perform the request and if the response
      * is of type `status`, decodes it.
      */
    def on[T](status: Status)(decoder: EntityDecoder[T])(implicit client: Client): Task[Result[T]] =
      client.decode(request){
        case Response(s,_,_,_,_) if s == status => decoder
        case Response(s,_,_,b,_)                => EntityDecoder.error(BadResponse(s, ""))
      }

    /** Generate a Task which, when executed, will perform the request and attempt to decode it */
    def decode[T](f: Response => EntityDecoder[T])(implicit client: Client): Task[Result[T]] =
      client.decode(request)(f)
  }
}
