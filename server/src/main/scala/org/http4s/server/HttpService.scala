package org.http4s
package server

import scalaz.concurrent.Task

/**
   * A [[HttpService]] that produces a Task to compute a [[Response]] from a
   * [[Request]].  An HttpService can be run on any supported http4s
   * server backend, such as Blaze, Jetty, or Tomcat.
   */
class HttpService(val run: Request => Task[Response]) extends AnyVal {

  def apply(req: Request): Task[Response] = run(req)

  def contraMap(f: Request => Request): HttpService = HttpService.lift(f andThen run)

  def flatMapM(f: Response => Task[Response]): HttpService = HttpService.lift(run.andThen(_.flatMap(f)))

  def map(f: Response => Response): HttpService = HttpService.lift(run.andThen(_.map(f)))


  def orElse(service2: HttpService): HttpService = HttpService.lift { req =>
    run(req).flatMap { resp =>
      if (resp.status == Status.NotFound) service2(req)
      else Task.now(resp)
    }
  }
}

object HttpService {
  /**
   * Lifts a partial function to an `HttpService`, answering with a [[Response]]
   * with status [[Status.NotFound]] for any requests where the function
   * is undefined.
   */
  def liftPF(pf: PartialFunction[Request, Task[Response]]): HttpService =
    HttpService.lift(req => pf.applyOrElse(req, {_: Request => notFound}))

  def lift(f: Request => Task[Response]): HttpService = new HttpService(f)

  val notFound: Task[Response] = Task.now(Response(Status.NotFound).withBody("404 Not Found.").run)
  val empty   : HttpService    = HttpService.lift(_ => notFound)
}

