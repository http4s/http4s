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
  *   val req2 = r.run  // Each invocation fetches a new Result based on the behavior of the Client
  *
  * }}}
  */

package object client {

  /** ClientSyntax provides the most convenient way to transform a [[Request]] into a [[Response]]
    *
    * @param request a [[Request]]
    */
  implicit def clientRequestSyntax(request: Request)(implicit client: Client): ResultSyntax =
    asyncRequestSyntax(Task.now(request))

  /** ClientSyntax provides the most convenient way to transform a [[Request]] into a [[Response]]
    *
    * @param uri a [[Uri]] that will form a GET request
    */
  implicit def clientUriSyntax(uri: Uri)(implicit client: Client): ResultSyntax =
    clientRequestSyntax(Request(uri = uri))


  implicit def asyncRequestSyntax(request: Task[Request])(implicit client: Client): ResultSyntax =
    new ResultSyntax(client.prepare(request))

  /** ResultSyntax provides the most convenient way to manipulate an asynchronous [[Response]]
    *
    * @param response a `Task` that will generate a [[Response]]
    */
  implicit final class ResultSyntax(val response: Task[Response]) extends AnyVal {

    /** Generate a Task which, when executed, will perform the request and if the response
      * is of type `status`, decodes it.
      */
    def on[T](status: Status, s2: Status*)(decoder: EntityDecoder[T]): Task[Result[T]] = {
      withDecoder { resp =>
        val s = resp.status
        if (s == status || s2.contains(s)) decoder
        else badStatus(s)
      }
    }

    /** Decode the [[Response]] based on [[Status]] */
    def onStatus[T](f: PartialFunction[Status, EntityDecoder[T]]): Task[Result[T]] =
      withDecoder { resp => f.applyOrElse(resp.status, badStatus) }

    /** Generate a Task which, when executed, will perform the request and attempt to decode it */
    def withDecoder[T](f: Response => EntityDecoder[T]): Task[Result[T]] =
      Client.withDecoder(response, f)

    /** Generate a Task which, when executed, will transform the [[Response]] to a [[Result]] */
    def toResult[T](f: Response => Task[Result[T]]): Task[Result[T]] =
      Client.toResult(response, f)

    private def badStatus(s: Status) = EntityDecoder.error(InvalidResponseException(s"Unhandled Status: $s"))
  }

}
