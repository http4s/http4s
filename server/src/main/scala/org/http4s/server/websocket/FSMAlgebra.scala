package org.http4s.server.websocket

import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import cats.effect.{Concurrent, Sync}
import cats.syntax.all._
import fs2.async.mutable.Queue
import fs2.Stream
import org.http4s.server.websocket.WebsocketMsg._
import org.http4s.websocket.WebsocketBits._

private[http4s] abstract class FSMAlgebra[F[_]] {

  def getState: F[State]

  def clearState(): F[Unit]

  def lastText(content: Array[Byte]): F[WebsocketMsg]

  def lastBinary(content: Array[Byte]): F[WebsocketMsg]

  def fragmentedBinary(content: Array[Byte]): F[Unit]

  def fragmentedText(content: String): F[Unit]

  def enqueueMsg(w: WebSocketFrame): F[Unit]

  def out: Stream[F, WebSocketFrame]
}

private[http4s] object FSMAlgebra {

  def apply[F[_]](implicit F: Concurrent[F]): F[FSMAlgebra[F]] =
    for {
      byteQueue <- Queue.unbounded[F, Option[Array[Byte]]]
      msgQueue <- Queue.unbounded[F, WebSocketFrame]
      stateRef <- F.delay(new AtomicReference[State](Empty))
      len <- F.delay(new AtomicInteger(0))
    } yield new Impl[F](byteQueue, msgQueue, stateRef, len)

  private class Impl[F[_]](
      byteQueue: Queue[F, Option[Array[Byte]]],
      msgQueue: Queue[F, WebSocketFrame],
      state: AtomicReference[State],
      msgLen: AtomicInteger,
  )(implicit F: Sync[F])
      extends FSMAlgebra[F] {
    private[this] def terminateBytes: F[Unit] =
      byteQueue.enqueue1(None)

    private[this] def foldBytesToArray(lastBytes: Array[Byte]): F[Array[Byte]] =
      F.delay(msgLen.get()).flatMap { len =>
        byteQueue.dequeue.unNoneTerminate.compile
          .fold(new ByteArrayAggregator(len + lastBytes.length))(_.aggregate(_))
          .map(_.aggregate(lastBytes).emit)
      }

    private[this] def compareAndSetState(old: State, ns: State): F[Boolean] =
      F.delay(state.compareAndSet(old, ns))

    private[this] def clearMsgLen(): F[Unit] =
      F.delay(msgLen.set(0))

    private[this] def incrementMsgLen(i: Int) =
      F.delay({ msgLen.getAndAdd(i); () })

    def getState: F[State] = F.delay(state.get())

    def clearState(): F[Unit] = F.delay(state.set(Empty))

    def lastText(content: Array[Byte]): F[WebsocketMsg] =
      for {
        _ <- terminateBytes
        bytes <- foldBytesToArray(content)
        _ <- clearMsgLen()
      } yield TextMsg(new String(bytes, UTF_8))

    def lastBinary(content: Array[Byte]): F[WebsocketMsg] =
      for {
        _ <- terminateBytes
        bytes <- foldBytesToArray(content)
        _ <- clearMsgLen()
      } yield BinaryMsg(bytes)

    def fragmentedBinary(content: Array[Byte]): F[Unit] =
      for {
        _ <- compareAndSetState(Empty, BufferingBinary)
        _ <- incrementMsgLen(content.length)
        _ <- byteQueue.enqueue1(Some(content))
      } yield ()

    def fragmentedText(content: String): F[Unit] = {
      val bytes = content.getBytes(UTF_8)
      for {
        _ <- compareAndSetState(Empty, BufferingText)
        _ <- incrementMsgLen(bytes.length)
        _ <- byteQueue.enqueue1(Some(bytes))
      } yield ()
    }

    def enqueueMsg(w: WebSocketFrame): F[Unit] = msgQueue.enqueue1(w)

    def out: Stream[F, WebSocketFrame] = msgQueue.dequeue
  }

  private class ByteArrayAggregator(size: Int) {
    require(size > 0)
    private[this] val internal = new Array[Byte](size)
    private[this] var nextIx: Int = 0
    def aggregate(arr: Array[Byte]): ByteArrayAggregator =
      if (nextIx + arr.length > size)
        throw new ArrayIndexOutOfBoundsException("Size will exceed append size")
      else {
        System.arraycopy(arr, 0, internal, nextIx, arr.length)
        nextIx += arr.length
        this
      }

    def emit: Array[Byte] = internal
  }
}
