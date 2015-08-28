package org.http4s

import scalaz.Kleisli
import scalaz.concurrent.Task
import scalaz.syntax.kleisli._

package object server {

  /**
   * A Service wraps a function of request type [[A]] to a Task that runs
   * to esponse type [[B]].  By wrapping the `Service`, we can compose them
   * using Kleisli operations.
   */
  type Service[A, B] = Kleisli[Task, A, B]

  object Service {
    /**
     * Lifts an unwrapped function that returns a Task into a [[Service]].
     *
     * @see [[HttpService.apply]]
     */
    def lift[A, B](f: A => Task[B]): Service[A, B] = Kleisli.kleisli(f)

    /**
      * Lifts a Task into a [[Service]].
      *
      */
    def const[A, B](b: => Task[B]): Service[A, B] = b.liftKleisli

    /**
      *  Lifts a value into a [[Service]].
      *
      */
    def constVal[A, B](b: => B): Service[A, B] = Task.now(b).liftKleisli

    /**
      * Allows Service chainig through an implicit [[Fallthrough]] instance.
      *
      */
    def withFallback[A, B : Fallthrough](fallback: Service[A, B])(service: Service[A, B]): Service[A, B] =
      service.flatMap(resp => Fallthrough[B].fallthrough(resp, fallback))

  }

  /**
   * A [[Service]] that produces a Task to compute a [[Response]] from a
   * [[Request]].  An HttpService can be run on any supported http4s
   * server backend, such as Blaze, Jetty, or Tomcat.
   */
  type HttpService = Service[Request, Response]

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

    val notFound: Task[Response] = Task.now(Response(Status.NotFound).withBody("404 Not Found.").run)
    val empty   : HttpService    = Service.const(notFound)
  }

  /**
   * A middleware is a function of one [[Service]] to another, possibly of a
   * different [[Request]] and [[Response]] type.  http4s comes with several
   * middlewares for composing common functionality into services.
   *
   * @tparam A the request type of the original service
   * @tparam B the response type of the original service
   * @tparam C the request type of the resulting service
   * @tparam D the response type of the original service
   */
  type Middleware[A, B, C, D] = Service[A, B] => Service[C, D]

  object Middleware {
    def apply[A, B, C, D](f: (C, Service[A, B]) => Task[D]): Middleware[A, B, C, D] = {
      service => Service.lift {
        req => f(req, service)
      }
    }
  }

  /**
   * An HTTP middleware converts an [[HttpService]] to another.
   */
  type HttpMiddleware = Middleware[Request, Response, Request, Response]
}


