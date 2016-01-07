package org.http4s

import scalaz.concurrent.Task

/**
  * There are 4 HttpService constructors:
  * <ul>
  *  <li>(Request => Task[Response]) => HttpService</li>
  *  <li>PartialFunction[Request, Task[Response]] => HttpService</li>
  *  <li>(PartialFunction[Request, Task[Response]], HttpService) => HttpService</li>
  *  <li>(PartialFunction[Request, Task[Response]], Task[Response]) => HttpService</li>
  * </ul>
  */
object HttpService {

  /** Alternative application which lifts a partial function to an `HttpService`,
    * answering with a [[Response]] with status [[Status.NotFound]] for any requests
    * where the function is undefined.
    */
  def apply(pf: PartialFunction[Request, Task[Response]], default: HttpService = empty): HttpService =
    Service.lift(req => pf.applyOrElse(req, default))

  /** Alternative application  which lifts a partial function to an `HttpService`,
    * answering with a [[Response]] as supplied by the default argument.
    */
  def apply(pf: PartialFunction[Request, Task[Response]], default: Task[Response]): HttpService =
    Service.lift(req => pf.applyOrElse(req, (_: Request) => default))

  /**
    * Lifts a (total) function to an `HttpService`. The function is expected to handle
    * ALL requests it is given.
    */
  def lift(f: Request => Task[Response]): HttpService = Service.lift(f)

  /** The default 'Not Found' response used when lifting a partial function
    * to a [[HttpService]] or general 'not handled' results.
    *
    * This particular instance is tagged with an so that it interacts appropriately
    * attribute to play well with the default [[Fallthrough]] behavior.
    */
  val notFound: Task[Response] = Task.now(Response(Status.NotFound)
                                             .withAttribute(Fallthrough.fallthroughKey, ())
                                             .withBody("404 Not Found.").run)

  val empty   : HttpService    = Service.const(notFound)
}

