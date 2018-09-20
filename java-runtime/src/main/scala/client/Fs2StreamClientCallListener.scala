package org.lyranthe.fs2_grpc
package java_runtime
package client

import cats.effect.{Effect, ConcurrentEffect}
import cats.implicits._
import fs2.{Pull, Stream}
import fs2.concurrent.Queue
import io.grpc.{ClientCall, Metadata, Status}

class Fs2StreamClientCallListener[F[_]: Effect, Response](
  request: Int => Unit, queue: Queue[F, Either[GrpcStatus, Response]]
) extends ClientCall.Listener[Response] {

  override def onMessage(message: Response): Unit = {
    request(1)
    queue.enqueue1(message.asRight).unsafeRun()
  }



  override def onClose(status: Status, trailers: Metadata): Unit =
    queue.enqueue1(GrpcStatus(status, trailers).asLeft).unsafeRun()

  def stream: Stream[F, Response] = {

    def go(q: Stream[F, Either[GrpcStatus, Response]]): Pull[F, Response, Unit] = {
      q.pull.uncons1.flatMap {
        case Some((Right(v), tl)) => Pull.output1(v) >> go(tl)
        case Some((Left(GrpcStatus(status, trailers)), _)) =>
          if (!status.isOk)
            Pull.raiseError[F](status.asRuntimeException(trailers))
          else
            Pull.done
        case None => Pull.done
      }
    }

    go(queue.dequeue).stream
  }
}

object Fs2StreamClientCallListener {

  def apply[F[_]: ConcurrentEffect, Response](request: Int => Unit): F[Fs2StreamClientCallListener[F, Response]] =
      Queue.unbounded[F, Either[GrpcStatus, Response]].map(new Fs2StreamClientCallListener[F, Response](request, _))

}
