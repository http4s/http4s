package org.http4s
package server
package middleware

import cats._
import cats.effect._
import cats.implicits._
import fs2._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{Duration, FiniteDuration}

object Timeout {

  /** Transform the service to return whichever resolves first: the
    * provided F[Response], or the service response task.  The
    * service response task continues to run in the background.  To
    * interrupt a server side response safely, look at
    * `scalaz.stream.wye.interrupt`.
    *
    * @param timeoutResponse Task[Response] to race against the result of the service. This will be run for each [[Request]]
    * @param service [[org.http4s.HttpService]] to transform
    */
  private def race[F[_]: Effect](timeoutResponse: F[Response[F]])
                                (service: HttpService[F])
                                (implicit ec: ExecutionContext): HttpService[F] =
    service.mapF { resp =>
      async.race(resp, timeoutResponse).map(_.merge)
    }

  /** Creates an `F` that is scheduled to return `response` after `timeout`.
    */
  private def delay[F[_]](duration: FiniteDuration, response: F[Response[F]])
                            (implicit F: Effect[F], scheduler: Scheduler): F[Response[F]] =
    F.async { (cb: (Either[Throwable, F[Response[F]]]) => Unit) =>
      scheduler.scheduleOnce(duration)(cb(Right(response)))
    }.flatten

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
  def apply[F[_]: Effect](timeout: Duration, response: F[Response[F]])
                         (service: HttpService[F])
                         (implicit scheduler: Scheduler, ec: ExecutionContext): HttpService[F] =
    timeout match {
      case fd: FiniteDuration => race(delay(fd, response))(service)
      case _                  => service
    }

  def apply[F[_]: Effect](timeout: Duration)
                         (service: HttpService[F])
                         (implicit scheduler: Scheduler, ec: ExecutionContext): HttpService[F] =
    apply(timeout, Response[F](Status.InternalServerError).withBody("The service timed out."))(service)
}
