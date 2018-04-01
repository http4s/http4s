package org.lyranthe.fs2_grpc.java_runtime.server

import cats.arrow.FunctionK
import cats.effect._
import cats.implicits._
import io.grpc._
import fs2._

import scala.concurrent.ExecutionContext

class Fs2StreamServerCallListener[F[_], Request, Response] private (
    queue: async.mutable.Queue[IO, Option[Request]],
    val call: Fs2ServerCall[F, Request, Response])(implicit F: Effect[F])
    extends ServerCall.Listener[Request]
    with Fs2ServerCallListener[F, Stream[F, ?], Request, Response] {
  override def onMessage(message: Request): Unit = {
    call.call.request(1)
    queue.enqueue1(message.some).unsafeRunSync()
  }

  override def onHalfClose(): Unit = queue.enqueue1(none).unsafeRunSync()

  override def onComplete(): Unit = queue.enqueue1(none).unsafeRunSync()

  override def source: Stream[F, Request] =
    queue.dequeue.unNoneTerminate.translate(FunctionK.lift(F.liftIO _))
}

object Fs2StreamServerCallListener {
  class PartialFs2StreamServerCallListener[F[_]](val dummy: Boolean = false) extends AnyVal {
    def unsafeCreate[Request, Response](call: ServerCall[Request, Response])(
        implicit F: Effect[F],
        ec: ExecutionContext): Fs2StreamServerCallListener[F, Request, Response] = {
      async
        .unboundedQueue[IO, Option[Request]]
        .map(new Fs2StreamServerCallListener[F, Request, Response](_, Fs2ServerCall[F, Request, Response](call)))
        .unsafeRunSync()
    }
  }

  def apply[F[_]] = new PartialFs2StreamServerCallListener[F]

}
