package org.lyranthe.fs2_grpc
package java_runtime
package server

import cats.effect.{ConcurrentEffect, Effect}
import cats.implicits._
import io.grpc.ServerCall
import fs2.concurrent.Queue
import fs2._

class Fs2StreamServerCallListener[F[_], Request, Response] private (
    queue: Queue[F, Option[Request]],
    val call: Fs2ServerCall[F, Request, Response])(implicit F: Effect[F])
    extends ServerCall.Listener[Request]
    with Fs2ServerCallListener[F, Stream[F, ?], Request, Response] {

  override def onMessage(message: Request): Unit = {
    call.call.request(1)
    queue.enqueue1(message.some).unsafeRun()
  }

  override def onHalfClose(): Unit = queue.enqueue1(none).unsafeRun()

  override def source: Stream[F, Request] =
    queue.dequeue.unNoneTerminate
}

object Fs2StreamServerCallListener {
  class PartialFs2StreamServerCallListener[F[_]](val dummy: Boolean = false) extends AnyVal {

    def apply[Request, Response](call: ServerCall[Request, Response])(
      implicit F: ConcurrentEffect[F]
    ): F[Fs2StreamServerCallListener[F, Request, Response]] = {
      Queue.unbounded[F, Option[Request]].
        map(new Fs2StreamServerCallListener[F, Request, Response](_, Fs2ServerCall[F, Request, Response](call)))
    }
  }

  def apply[F[_]] = new PartialFs2StreamServerCallListener[F]

}
