package org.lyranthe.fs2_grpc
package java_runtime
package client

import cats.effect._
import cats.effect.concurrent.{Deferred, Ref}
import cats.implicits._
import io.grpc._

class Fs2UnaryClientCallListener[F[_], Response](
  grpcStatus: Deferred[F, GrpcStatus],
  value: Ref[F, Option[Response]])(implicit F: Effect[F]) extends ClientCall.Listener[Response] {

  override def onClose(status: Status, trailers: Metadata): Unit =
    grpcStatus.complete(GrpcStatus(status, trailers)).unsafeRun()

  override def onMessage(message: Response): Unit =
    value.set(message.some).unsafeRun()

  def getValue: F[Response] = {
     for {
      r <- grpcStatus.get
      v <- value.get
      result <- {
        if (!r.status.isOk)
          F.raiseError(r.status.asRuntimeException(r.trailers))
        else {
          v match {
            case None =>
              F.raiseError(
                Status.INTERNAL
                  .withDescription("No value received for unary call")
                  .asRuntimeException(r.trailers))
            case Some(v1) =>
              F.pure(v1)
          }
        }
      }
    } yield result
  }
}

object Fs2UnaryClientCallListener {

  def apply[F[_]: ConcurrentEffect, Response]: F[Fs2UnaryClientCallListener[F, Response]] = {
    (Deferred[F, GrpcStatus], Ref.of[F, Option[Response]](none)).mapN((response, value) =>
      new Fs2UnaryClientCallListener[F, Response](response, value)
    )
  }

}
