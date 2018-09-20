package org.lyranthe.fs2_grpc
package java_runtime
package server

import cats.effect.{ConcurrentEffect, Effect}
import cats.effect.concurrent.{Deferred, Ref}
import cats.syntax.all._
import io.grpc._

class Fs2UnaryServerCallListener[F[_], Request, Response] private (
    value: Ref[F, Option[Request]],
    isComplete: Deferred[F, Unit],
    val call: Fs2ServerCall[F, Request, Response])(implicit F: Effect[F])
    extends ServerCall.Listener[Request] with Fs2ServerCallListener[F, F, Request, Response] {

  import Fs2UnaryServerCallListener._

  override def onMessage(message: Request): Unit = {

    value.access.flatMap[Unit] {
      case (curValue, modify) =>
        if (curValue.isDefined)
          F.raiseError(statusException(TooManyRequests))
        else
          modify(message.some).void
    }.unsafeRun()

  }

  override def onHalfClose(): Unit =
    isComplete.complete(()).unsafeRun()

  override def source: F[Request] =
    for {
      _           <- isComplete.get
      valueOrNone <- value.get
            value <- valueOrNone.fold[F[Request]](F.raiseError(statusException(NoMessage)))(F.pure)
    } yield value
}

object Fs2UnaryServerCallListener {

  val TooManyRequests: String = "Too many requests"
  val NoMessage: String       = "No message for unary call"

  private val statusException: String => StatusRuntimeException = msg =>
    new StatusRuntimeException(Status.INTERNAL.withDescription(msg))

  class PartialFs2UnaryServerCallListener[F[_]](val dummy: Boolean = false) extends AnyVal {

    def apply[Request, Response](call: ServerCall[Request, Response])(
      implicit F: ConcurrentEffect[F]
    ): F[Fs2UnaryServerCallListener[F, Request, Response]] =
      (Deferred[F, Unit], Ref.of[F, Option[Request]](none)).mapN((promise, ref) =>
        new Fs2UnaryServerCallListener[F, Request, Response](ref, promise, Fs2ServerCall[F, Request, Response](call))
      )
  }

  def apply[F[_]] = new PartialFs2UnaryServerCallListener[F]
}
