package org.http4s

import scalaz.Kleisli
import scalaz.concurrent.Task

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
     * No effort is made to provide a default response if a [[PartialFunction]]
     * is passed in.
     *
     * @see [[HttpService.apply]]
     */
    def lift[A, B](f: A => Task[B]): Service[A, B] = Kleisli.kleisli(f)
  }

  /**
   * A [[Service]] that returns an optional result.
   */
  type PartialService[A, B] = Service[A, Option[B]]

  /**
   * A [[Service]] that produces a Task to compute a [[Response]] from a
   * [[Request]].  An HttpService can be run on any supported http4s
   * server backend, such as Blaze, Jetty, or Tomcat.
   */
  type HttpService = Service[Request, Response]

  object HttpService {
    /**
     * Lifts a partial function to an `HttpService`, answering with a [[Response]]
     * with status [[Status.NotFound]] for any requests where the function
     * is undefined.
     */
    def apply(pf: PartialFunction[Request, Task[Response]]): HttpService =
      Service.lift {
        pf.lift.andThen {
          case Some(respTask) => respTask
          case None => Task.now(Response(Status.NotFound))
        }
      }

    val empty: HttpService = Service.lift(Function.const(Task.now(Response(Status.NotFound))))
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


