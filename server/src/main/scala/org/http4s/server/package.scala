package org.http4s

import scalaz.Kleisli
import scalaz.concurrent.Task

package object server {
  type Service[A, B] = Kleisli[Task, A, B]

  object Service {
    def lift[A, B](f: A => Task[B]): Service[A, B] = Kleisli.kleisli(f)
  }

  type PartialService[A, B] = Service[A, Option[B]]

  type HttpService = Service[Request, Response]
  object HttpService {
    /**
     * Lifts a partial function to a service.  The resulting service will
     * return a Task containing `None` where the partial function is not
     * defined, and Some result where it is.
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

  implicit class HttpServiceSyntax(val service: HttpService) extends AnyVal {
    def orElse(that: HttpService): HttpService = Service.lift { req: Request =>
      service(req).flatMap {
        case resp: Response if resp.status == Status.NotFound =>
          that(req)
        case resp =>
          Task.now(resp)
      }
    }
  }

  type Middleware[A, B, C, D] = Service[A, B] => Service[C, D]
  object Middleware {
    def apply[A, B, C, D](f: (C, Service[A, B]) => Task[D]): Middleware[A, B, C, D] = {
      service => Service.lift {
        req => f(req, service)
      }
    }
  }

  type HttpMiddleware = Middleware[Request, Response, Request, Response]
}


