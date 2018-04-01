package org.lyranthe.fs2_grpc.java_runtime.server

import cats.effect._
import cats.implicits._
import fs2._
import io.grpc._

import scala.concurrent.ExecutionContext

class Fs2UnaryServerCallListener[F[_], Request, Response] private (
    value: async.Ref[IO, Option[Request]],
    isComplete: async.Promise[IO, Unit],
    val call: Fs2ServerCall[F, Request, Response])(implicit F: Effect[F])
    extends ServerCall.Listener[Request]
    with Fs2ServerCallListener[F, F, Request, Response] {
  override def onMessage(message: Request): Unit = {
    value.access
      .flatMap {
        case (curValue, modify) =>
          if (curValue.isDefined)
            IO.raiseError(
              new StatusRuntimeException(Status.INTERNAL
                .withDescription(Fs2UnaryServerCallListener.TooManyRequests)))
          else
            modify(message.some)
      }
      .unsafeRunSync()
    ()
  }

  override def onHalfClose(): Unit = isComplete.complete(()).unsafeRunSync()

  override def source: F[Request] =
    F.liftIO(for {
      _           <- isComplete.get
      valueOrNone <- value.get
      value <- valueOrNone.fold[IO[Request]](
        IO.raiseError(
          new StatusRuntimeException(Status.INTERNAL.withDescription(Fs2UnaryServerCallListener.NoMessage))))(IO.pure)
    } yield value)
}

object Fs2UnaryServerCallListener {
  final val TooManyRequests: String = "Too many requests"
  final val NoMessage: String       = "No message for unary call"

  class PartialFs2UnaryServerCallListener[F[_]](val dummy: Boolean = false) extends AnyVal {
    def unsafeCreate[Request, Response](call: ServerCall[Request, Response])(
        implicit F: Effect[F],
        ec: ExecutionContext): Fs2UnaryServerCallListener[F, Request, Response] = {
      val listener = for {
        ref     <- async.refOf[IO, Option[Request]](none)
        promise <- async.promise[IO, Unit]
      } yield
        new Fs2UnaryServerCallListener[F, Request, Response](ref, promise, Fs2ServerCall[F, Request, Response](call))

      listener.unsafeRunSync()
    }
  }

  def apply[F[_]] = new PartialFs2UnaryServerCallListener[F]
}
