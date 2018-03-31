package org.lyranthe.grpc.java_runtime.server

import cats.effect._
import cats.implicits._
import fs2._
import fs2.async.Promise.AlreadyCompletedException
import io.grpc._

import scala.concurrent.ExecutionContext

class Fs2UnaryServerCallListener[F[_], Request, Response] private (
    value: async.Promise[IO, Request],
    val call: Fs2ServerCall[F, Request, Response])(implicit F: Effect[F])
    extends ServerCall.Listener[Request]
    with Fs2ServerCallListener[F, F, Request, Response] {
  override def onMessage(message: Request): Unit = {
    value
      .complete(message)
      .adaptError {
        case ex: AlreadyCompletedException =>
          new StatusRuntimeException(
            Status.INTERNAL
              .withDescription(Fs2UnaryServerCallListener.TooManyRequests)
              .withCause(ex))
      }
      .unsafeRunSync()
  }

  override def source: F[Request] =
    F.liftIO(value.get)
}

object Fs2UnaryServerCallListener {
  final val TooManyRequests: String = "Too many requests"

  class PartialFs2UnaryServerCallListener[F[_]](val dummy: Boolean = false) extends AnyVal {
    def unsafeCreate[Request, Response](call: ServerCall[Request, Response])(
        implicit F: Effect[F],
        ec: ExecutionContext): Fs2UnaryServerCallListener[F, Request, Response] = {
      async
        .promise[IO, Request]
        .map(new Fs2UnaryServerCallListener[F, Request, Response](_, Fs2ServerCall[F, Request, Response](call)))
        .unsafeRunSync()
    }
  }

  def apply[F[_]] = new PartialFs2UnaryServerCallListener[F]
}
