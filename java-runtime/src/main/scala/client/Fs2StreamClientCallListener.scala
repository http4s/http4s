package org.lyranthe.fs2_grpc.java_runtime.client

import cats.arrow.FunctionK
import cats.effect.{IO, LiftIO}
import cats.implicits._
import fs2.{Pull, Stream}
import io.grpc.{ClientCall, Metadata, Status}

import scala.concurrent.ExecutionContext

class Fs2StreamClientCallListener[Response](request: Int => Unit,
                                            queue: fs2.async.mutable.Queue[IO, Either[GrpcStatus, Response]])
    extends ClientCall.Listener[Response] {
  override def onMessage(message: Response): Unit = {
    request(1)
    queue.enqueue1(message.asRight).unsafeRunSync()
  }

  override def onClose(status: Status, trailers: Metadata): Unit = {
    queue.enqueue1(GrpcStatus(status, trailers).asLeft).unsafeRunSync()
  }

  def stream[F[_]](implicit F: LiftIO[F]): Stream[F, Response] = {
    def go(q: Stream[F, Either[GrpcStatus, Response]]): Pull[F, Response, Unit] = {
      // TODO: Write in terms of Segment
      q.pull.uncons1.flatMap {
        case Some((Right(v), tl)) => Pull.output1(v) >> go(tl)
        case Some((Left(GrpcStatus(status, trailers)), _)) =>
          if (!status.isOk)
            Pull.raiseError(status.asRuntimeException(trailers))
          else
            Pull.done
        case None => Pull.done
      }
    }

    go(queue.dequeue.translate(FunctionK.lift(F.liftIO _))).stream
  }
}

object Fs2StreamClientCallListener {
  def apply[F[_], Response](request: Int => Unit)(implicit F: LiftIO[F],
                                                  ec: ExecutionContext): F[Fs2StreamClientCallListener[Response]] = {
    F.liftIO(
      fs2.async
        .unboundedQueue[IO, Either[GrpcStatus, Response]]
        .map(new Fs2StreamClientCallListener[Response](request, _)))
  }
}
