package org.http4s
package server
package middleware

import java.util.concurrent._
import scala.concurrent._
import scala.concurrent.duration._

import fs2._
import org.http4s.batteries._
import org.http4s.util.threads.threadFactory

object Timeout {

  // TODO: this should probably be replaced with a low res executor to save clock cycles
  private val defaultScheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(
    1, threadFactory(name = l => s"http4s-timeout-scheduler-$l", daemon = true))

  val DefaultTimeoutResponse =
    Response(Status.InternalServerError)
      .withBody("The service timed out.")

  private def timeoutResp(timeout: FiniteDuration, response: Task[Response], customScheduler: Option[ScheduledExecutorService]): Task[Response] = {
    val schedulerService = customScheduler.getOrElse(defaultScheduler)
    implicit val scheduler: Scheduler = Scheduler.fromScheduledExecutorService(schedulerService)
    implicit val strategy: Strategy = Strategy.fromExecutor(schedulerService)
    response.schedule(timeout)
  }

  /** Transform the service to return whichever resolves first: the
    * provided Task[Response], or the service resposne task.  The
    * service response task continues to run in the background.  To
    * interrupt a server side response safely, look at
    * `scalaz.stream.wye.interrupt`.
    *
    * @param r Task[Response] to race against the result of the service. This will be run for each [[Request]]
    * @param service [[org.http4s.HttpService]] to transform
>>>>>>> release-0.16.x
    */
  def race(timeoutResponse: Task[Response], customScheduler: Option[ScheduledExecutorService] = None)(service: HttpService): HttpService = {
    val scheduler = customScheduler.getOrElse(defaultScheduler)
    implicit val s = Strategy.fromExecutor(scheduler)
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
    * @param customScheduler a custom scheduler to use for timing out the request.
    *   If `None` is provided, then a default daemon thread scheduler will be used.
    * @param service [[HttpService]] to transform
    */
  def apply(timeout: Duration, response: Task[Response] = DefaultTimeoutResponse, customScheduler: Option[ScheduledExecutorService] = None)(service: HttpService): HttpService =
    timeout match {
      case fd: FiniteDuration => race(timeoutResp(fd, response, customScheduler))(service)
      case _ => service
    }

  /** Transform the service to return a RequestTimeOut [[Status]] after 30 seconds.
    * ready.  The service response task continues to run in the
    * background.  To interrupt a server side response safely, look at
    * `scalaz.stream.wye.interrupt`.
    *
    * @param service [[HttpService]] to transform
    */
  @deprecated("Use the overload that also has response and customScheduler parameters (with default values provided)", "0.15")
  def apply(service: HttpService): HttpService = apply(30.seconds)(service)
}
