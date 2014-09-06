package org.http4s
package server
package middleware

import scala.concurrent.duration._
import scalaz.concurrent.Task
import java.util.concurrent.{TimeUnit, ScheduledThreadPoolExecutor}
import scalaz.{OptionT, \/-}

object Timeout {

  // TODO: this should probably be replaced with a low res executor to save clock cycles
  private val ec = new ScheduledThreadPoolExecutor(1)

  private def timeoutResp(timeout: Duration): Task[Response] = Task.async { cb =>
    val r = new Runnable { override def run(): Unit = cb(\/-(Response(Status.RequestTimeout))) }
    ec.schedule(r, timeout.toNanos, TimeUnit.NANOSECONDS)
  }

    /** Transform the service such to return whichever resolves first:
      * the provided Task[Response], or the result of the service
      * @param r Task[Response] to race against the result of the service. This will be run for each [[Request]]
      * @param service [[org.http4s.server.HttpService]] to transform
      */
  def apply(r: Task[Response]) (service: HttpService): HttpService = { req =>
    val optResp = service(req).run
    val timeoutResp = r.map(Some(_))
    OptionT.optionT(Task.taskInstance.chooseAny(optResp, timeoutResp :: Nil).map(_._1))
  }

  /** Transform the service to return a RequestTimeOut [[Status]] after the supplied Duration
    * @param timeout Duration to wait before returning the RequestTimeOut
    * @param service [[HttpService]] to transform
    */
  def apply(timeout: Duration)(service: HttpService): HttpService = {
    if (timeout.isFinite()) apply(timeoutResp(timeout))(service)
    else service
  }

  /** Transform the service to return a RequestTimeOut [[Status]] after 30 seconds
    * @param service [[HttpService]] to transform
    */
  def apply(service: HttpService): HttpService = apply(30.seconds)(service)
}