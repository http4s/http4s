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

  private def timeoutResp(timeout: Duration, response: Task[Response], customScheduler: Option[ScheduledExecutorService]): Task[Response] = {
    val scheduler = customScheduler.getOrElse(defaultScheduler)
    implicit val s = Strategy.fromExecutor(scheduler)
    Task.async[Task[Response]] { cb =>
      val r = new Runnable { override def run(): Unit = cb(Right(response)) }
      scheduler.schedule(r, timeout.toNanos, TimeUnit.NANOSECONDS)
      ()
    }.flatten
  }

  /** Transform the service such to return whichever resolves first:
    * the provided Task[Response], or the result of the service
    * @param timeoutResponse Task[Response] to race against the result of the service. This will be run for each [[Request]]
    * @param service [[org.http4s.server.HttpService]] to transform
    */
  def race(timeoutResponse: Task[Response], customScheduler: Option[ScheduledExecutorService] = None)(service: HttpService): HttpService = {
    val scheduler = customScheduler.getOrElse(defaultScheduler)
    implicit val s = Strategy.fromExecutor(scheduler)
    service.mapF { resp =>
      (resp race timeoutResponse).map(_.merge)
    }
  }

  /** Transform the service to return a RequestTimeOut [[Status]] after the supplied Duration
    * @param timeout Duration to wait before returning the RequestTimeOut
    * @param customScheduler a custom scheduler to use for timing out the request.
    *   If `None` is provided, then a default daemon thread scheduler will be used.
    * @param service [[HttpService]] to transform
    */
  def apply(timeout: Duration, response: Task[Response] = DefaultTimeoutResponse, customScheduler: Option[ScheduledExecutorService] = None)(service: HttpService): HttpService = {
    if (timeout.isFinite()) race(timeoutResp(timeout, response, customScheduler))(service)
    else service
  }

  /** Transform the service to return a RequestTimeOut [[Status]] after 30 seconds
    * @param service [[HttpService]] to transform
    */
  @deprecated("Use the overload that also has response and customScheduler parameters (with default values provided)", "0.15")
  def apply(service: HttpService): HttpService = apply(30.seconds)(service)
}
