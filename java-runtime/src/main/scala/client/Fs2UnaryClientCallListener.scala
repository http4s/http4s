package org.lyranthe.fs2_grpc.java_runtime.client

import cats.effect.{IO, LiftIO}
import cats.implicits._
import io.grpc._

import scala.concurrent.ExecutionContext

class Fs2UnaryClientCallListener[Response](grpcStatus: fs2.async.Promise[IO, GrpcStatus],
                                           value: fs2.async.Ref[IO, Option[Response]])
    extends ClientCall.Listener[Response] {
  override def onClose(status: Status, trailers: Metadata): Unit =
    grpcStatus.complete(GrpcStatus(status, trailers)).unsafeRunSync()

  override def onMessage(message: Response): Unit =
    value.setAsync(message.some).unsafeRunSync()

  def getValue[F[_]](implicit F: LiftIO[F]): F[Response] = {
    val result: IO[Response] = for {
      r <- grpcStatus.get
      v <- value.get
      result <- {
        if (!r.status.isOk)
          IO.raiseError(r.status.asRuntimeException(r.trailers))
        else {
          v match {
            case None =>
              IO.raiseError(
                Status.INTERNAL
                  .withDescription("No value received for unary call")
                  .asRuntimeException(r.trailers))
            case Some(v1) =>
              IO.pure(v1)
          }
        }
      }
    } yield result

    F.liftIO(result)
  }
}

object Fs2UnaryClientCallListener {
  def apply[F[_], Response](implicit F: LiftIO[F], ec: ExecutionContext): F[Fs2UnaryClientCallListener[Response]] = {
    F.liftIO(for {
      response <- fs2.async.promise[IO, GrpcStatus]
      value    <- fs2.async.refOf[IO, Option[Response]](None)
    } yield new Fs2UnaryClientCallListener[Response](response, value))
  }
}
