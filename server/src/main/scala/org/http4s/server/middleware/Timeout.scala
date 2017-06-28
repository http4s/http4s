package org.http4s
package server
package middleware

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{Duration, FiniteDuration}
import fs2.{Scheduler, Strategy, Task}

object Timeout {

  val DefaultTimeoutResponse =
    Response(Status.InternalServerError)
      .withBody("The service timed out.")

  /** Transform the service to return whichever resolves first: the
    * provided Task[Response], or the service resposne task.  The
    * service response task continues to run in the background.  To
    * interrupt a server side response safely, look at
    * `scalaz.stream.wye.interrupt`.
    *
    * @param timeoutResponse Task[Response] to race against the result of the service. This will be run for each [[Request]]
    * @param service [[org.http4s.HttpService]] to transform
    */
  private def race(timeoutResponse: Task[Response])(service: HttpService)(implicit scheduler: Scheduler, strategy: Strategy): HttpService = {
    service.mapF { resp =>
      (resp race timeoutResponse).map(_.merge)
    }
  }

  /** Transform the service to return a timeout response [[Status]]
    * after the supplied duration if the service response is not yet
    * ready.  The service response task continues to run in the
    * background.  To interrupt a server side response safely, look at
    * `scalaz.stream.wye.interrupt`.
    *
    * @param timeout Duration to wait before returning the
    * RequestTimeOut
    * @param service [[HttpService]] to transform
    */
  def apply(timeout: Duration, response: Task[Response] = DefaultTimeoutResponse)(service: HttpService)(implicit scheduler: Scheduler, ec: ExecutionContext): HttpService = {
    implicit val strategy = Strategy.fromExecutionContext(ec)
    timeout match {
      case fd: FiniteDuration => race(response.schedule(fd))(service)
      case _ => service
    }
  }
}
