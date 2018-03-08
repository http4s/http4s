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

  /** Transform the service to return whichever resolves first: the
    * provided F[Response[F]], or the service response task.  The
    * service response task continues to run in the background.  To
    * interrupt a server side response safely, look at
    * `scalaz.stream.wye.interrupt`.
    *
    * @param timeoutResponse F[Response] to race against the result of the service. This will be run for each [[Request]]
    * @param service [[org.http4s.HttpService]] to transform
    */
  private def race[F[_]: Effect](timeoutResponse: F[Response[F]])(service: HttpService[F])(
      implicit executionContext: ExecutionContext): HttpService[F] =
    service.mapF { resp =>
      OptionT(fs2AsyncRace(resp.value, timeoutResponse.map(_.some)).map(_.merge))
    }

  /**
    * Returns an effect that, when run, races evaluation of `fa` and `fb`,
    * and returns the result of whichever completes first. The losing effect
    * continues to execute in the background though its result will be sent
    * nowhere.
    *
    * Internalized from fs2 for now
    */
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
  def apply[F[_]: Effect](timeout: Duration, response: F[Response[F]])(service: HttpService[F])(
      implicit executionContext: ExecutionContext,
      scheduler: Scheduler): HttpService[F] =
    timeout match {
      case fd: FiniteDuration => race(scheduler.effect.delay(response, fd))(service)
      case _ => service
    }

  def apply[F[_]: Effect](timeout: Duration)(service: HttpService[F])(
      implicit executionContext: ExecutionContext,
      scheduler: Scheduler): HttpService[F] =
    apply(
      timeout,
      Response[F](Status.InternalServerError).withBody("The service timed out.").pure[F])(service)
}
