package org.http4s

import org.http4s.client.impl.{EmptyRequestGenerator, EntityRequestGenerator}

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
  *   val r2: Task[Result[String]] = GET("https://www.foo.bar/").on[String](Ok)   // implicitly resolve the decoder
  *   val req1 = r.run
  *   val req2 = r.run  // Each invocation fetches a new Result based on the behavior of the Client
  *
  * }}}
  */

package object client {
  import Method._

//  val GET: Method.GET.type = Method.GET
//  val HEAD: Method.HEAD.type = Method.HEAD
//  val POST: Method.POST.type = Method.POST
//  val PUT: Method.PUT.type = Method.PUT
//  val DELETE: Method.DELETE.type = Method.DELETE
//  val CONNECT: Method.CONNECT.type = Method.CONNECT
//  val OPTIONS: Method.OPTIONS.type = Method.OPTIONS
//  val TRACE: Method.TRACE.type = Method.TRACE
//  val PATCH: Method.PATCH.type = Method.PATCH

  implicit class GetSyntax(val method: Method.GET.type) extends AnyVal with EmptyRequestGenerator
  implicit class HeadSyntax(val method: HEAD.type) extends AnyVal with EmptyRequestGenerator
  implicit class PostSyntax(val method: POST.type) extends AnyVal with EntityRequestGenerator
  implicit class PutSyntax(val method: PUT.type) extends AnyVal with EntityRequestGenerator
  implicit class DeleteSyntax(val method: DELETE.type) extends AnyVal with EmptyRequestGenerator
  implicit class ConnectSyntax(val method: CONNECT.type) extends AnyVal with EmptyRequestGenerator
  implicit class OptionsSyntax(val method: OPTIONS.type) extends AnyVal with EntityRequestGenerator
  implicit class TraceSyntax(val method: TRACE.type) extends AnyVal with EmptyRequestGenerator
  implicit class PatchSyntax(val method: PATCH.type) extends AnyVal with EntityRequestGenerator

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

    /** Fail on a Response which do not match a designated status */
    def on(status: Status, s2: Status*): Task[Response] = response.map { resp =>
      val s = resp.status
      val valid = resp.attributes.get(statusKey) match {
        case Some(s) => s ++ s2 + status
        case None    => s2.toSet + status
      }

      resp.withAttribute(statusKey, valid)
    }

    /** Decode the [[Response]] to the specified type
      *
      * If no valid [[Status]] has been described, allow Ok
      * @param decoder [[EntityDecoder]] used to decode the [[Response]]
      * @tparam T type of the result
      * @return the `Task` which will generate the T
      */
    def as[T](implicit decoder: EntityDecoder[T]): Task[T] = response.flatMap { resp =>
      val validStatus = resp.attributes.get(statusKey) match {
        case Some(set) => set.contains(resp.status)
        case None      => resp.status == Status.Ok
      }

      if (validStatus) decoder(resp)
      else Task.fail(badStatusError(resp.status))
    }

    /** Decode the [[Response]] based on [[Status]] */
    def matchStatus[T](f: PartialFunction[Status, EntityDecoder[T]]): Task[T] =
      withDecoder { resp => f.applyOrElse(resp.status, badStatus) }

    /** Generate a Task which, when executed, will perform the request and attempt to decode it */
    def withDecoder[T](f: Response => EntityDecoder[T]): Task[T] =
      Client.withDecoder(response)(f)

    /** Generate a Task which, when executed, will transform the [[Response]] to a `T` */
    def toResult[T](f: Response => Task[T]): Task[T] =
      Client.toResult(response)(f)

    private def badStatus(s: Status) = EntityDecoder.error(badStatusError(s))

    private def badStatusError(s: Status) = InvalidResponseException(s"Unhandled Status: $s")
  }

  implicit def wHeadersDec[T](implicit decoder: EntityDecoder[T]): EntityDecoder[(Headers, T)] =
    EntityDecoder(resp => decoder.apply(resp).map(t => (resp.headers,t)), decoder.consumes.toSeq:_*)

  private val statusKey = AttributeKey[Set[Status]]("Valid statuses")
}
