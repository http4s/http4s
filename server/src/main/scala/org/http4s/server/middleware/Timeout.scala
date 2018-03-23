package org.http4s
package server
package middleware

import cats.data.OptionT
import cats.effect._
import cats.implicits._
import fs2._
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{Duration, FiniteDuration}

object Timeout {

  @deprecated("Exists to support deprecated methods", "0.19")
  private def race[F[_]: Effect](timeoutResponse: F[Response[F]])(service: HttpService[F])(
      implicit executionContext: ExecutionContext): HttpService[F] =
    service.mapF { resp =>
      OptionT(fs2AsyncRace(resp.value, timeoutResponse.map(_.some)).map(_.merge))
    }

  @deprecated("Exists to support deprecated methods", "0.19")
  private def fs2AsyncRace[F[_], A, B](fa: F[A], fb: F[B])(
      implicit F: Effect[F],
      ec: ExecutionContext): F[Either[A, B]] =
    async.promise[F, Either[Throwable, Either[A, B]]].flatMap { p =>
      def go: F[Unit] = F.delay {
        val refToP = new AtomicReference(p)
        val won = new AtomicBoolean(false)
        val win = (res: Either[Throwable, Either[A, B]]) => {
          // important for GC: we don't reference the promise directly, and the
          // winner destroys any references behind it!
          if (won.compareAndSet(false, true)) {
            val action = refToP.getAndSet(null).complete(res)
            async.unsafeRunAsync(action)(_ => IO.unit)
          }
        }

        async.unsafeRunAsync(fa.map(Left.apply))(res => IO(win(res)))
        async.unsafeRunAsync(fb.map(Right.apply))(res => IO(win(res)))
      }

      go *> p.get.flatMap(F.fromEither)
    }

  /** Transform the service to return a timeout response after the given
    * duration if the service has not yet responded.  If the timeout
    * fires, the service's response continues to run in the background
    * and is discarded.
    *
    * @param timeout Finite duration to wait before returning `response`
    * @param service [[HttpService]] to transform
    */
  @deprecated(
    "Use apply(FiniteDuration, F[Response[F]](HttpService[F]) instead. That cancels the losing effect.",
    "0.19")
  def apply[F[_]: Effect](timeout: Duration, response: F[Response[F]])(service: HttpService[F])(
      implicit executionContext: ExecutionContext,
      scheduler: Scheduler): HttpService[F] =
    timeout match {
      case fd: FiniteDuration => race(scheduler.effect.delay(response, fd))(service)
      case _ => service
    }

  /** Transform the service to return a timeout response after the given
    * duration if the service has not yet responded.  If the timeout
    * fires, the service's response continues to run in the background
    * and is discarded.
    *
    * @param timeout Finite duration to wait before returning `response`
    */
  @deprecated(
    "Use apply(FiniteDuration)(HttpService[F]) instead. That cancels the losing effect.",
    "0.19")
  def apply[F[_]: Effect](timeout: Duration)(service: HttpService[F])(
      implicit executionContext: ExecutionContext,
      scheduler: Scheduler): HttpService[F] =
    apply(timeout, Response[F](Status.InternalServerError).withBody("The service timed out."))(
      service)

  /** Transform the service to return a timeout response after the given
    * duration if the service has not yet responded.  If the timeout
    * fires, the service's response is canceled.
    *
    * @param timeout Finite duration to wait before returning a `500
    * Internal Server Error` response
    * @param service [[HttpService]] to transform
    */
  def apply[F[_]](timeout: FiniteDuration, timeoutResponse: F[Response[F]])(
      service: HttpService[F])(implicit F: Concurrent[F], T: Timer[F]): HttpService[F] = {
    val OTC = Concurrent[OptionT[F, ?]]
    service
      .mapF(respF => OTC.race(respF, OptionT.liftF(T.sleep(timeout) *> timeoutResponse)))
      .map(_.merge)
  }

  /** Transform the service to return a timeout response after the given
    * duration if the service has not yet responded.  If the timeout
    * fires, the service's response is canceled.
    *
    * @param timeout Finite duration to wait before returning a `500
    * Internal Server Error` response
    * @param service [[HttpService]] to transform
    */
  def apply[F[_]](timeout: FiniteDuration)(
      service: HttpService[F])(implicit F: Concurrent[F], T: Timer[F]): HttpService[F] =
    apply(timeout, Response[F](Status.InternalServerError).withBody("The service timed out."))(
      service)
}
