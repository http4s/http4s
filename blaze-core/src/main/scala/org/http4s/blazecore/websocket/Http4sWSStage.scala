/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package blazecore
package websocket

import cats.effect._
import cats.effect.std.{Dispatcher, Semaphore}
import cats.syntax.all._
import fs2._
import fs2.concurrent.SignallingRef
import java.util.concurrent.atomic.AtomicBoolean
import org.http4s.blaze.pipeline.{LeafBuilder, TailStage, TrunkBuilder}
import org.http4s.blaze.pipeline.Command.EOF
import org.http4s.blaze.util.Execution.{directec, trampoline}
import org.http4s.websocket.{
  ReservedOpcodeException,
  UnknownOpcodeException,
  WebSocket,
  WebSocketCombinedPipe,
  WebSocketFrame,
  WebSocketSeparatePipe
}
import org.http4s.websocket.WebSocketFrame._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

private[http4s] class Http4sWSStage[F[_]](
    ws: WebSocket[F],
    sentClose: AtomicBoolean,
    deadSignal: SignallingRef[F, Boolean],
    writeSemaphore: Semaphore[F],
    dispatcher: Dispatcher[F]
)(implicit F: Async[F])
    extends TailStage[WebSocketFrame] {

  def name: String = "Http4s WebSocket Stage"

  //////////////////////// Source and Sink generators ////////////////////////
  val isClosed: F[Boolean] = F.delay(sentClose.get())
  val setClosed: F[Boolean] = F.delay(sentClose.compareAndSet(false, true))

  def evalFrame(frame: WebSocketFrame): F[Unit] = frame match {
    case c: Close => setClosed.ifM(writeFrame(c, directec), F.unit)
    case _ => writeFrame(frame, directec)
  }

  def snkFun(frame: WebSocketFrame): F[Unit] = isClosed.ifM(F.unit, evalFrame(frame))

  private[this] def writeFrame(frame: WebSocketFrame, ec: ExecutionContext): F[Unit] =
    writeSemaphore.permit.use { _ =>
      F.async_[Unit] { cb =>
        channelWrite(frame).onComplete {
          case Success(res) => cb(Right(res))
          case Failure(t) => cb(Left(t))
        }(ec)
      }
    }

  private[this] def readFrameTrampoline: F[WebSocketFrame] =
    F.async_[WebSocketFrame] { cb =>
      channelRead().onComplete {
        case Success(ws) => cb(Right(ws))
        case Failure(exception) => cb(Left(exception))
      }(trampoline)
    }

  /** Read from our websocket.
    *
    * To stay faithful to the RFC, the following must hold:
    *
    * - If we receive a ping frame, we MUST reply with a pong frame
    * - If we receive a pong frame, we don't need to forward it.
    * - If we receive a close frame, it means either one of two things:
    *   - We sent a close frame prior, meaning we do not need to reply with one. Just end the stream
    *   - We are the first to receive a close frame, so we try to atomically check a boolean flag,
    *     to prevent sending two close frames. Regardless, we set the signal for termination of
    *     the stream afterwards
    *
    * @return A websocket frame, or a possible IO error.
    */
  private[this] def handleRead(): F[WebSocketFrame] = {
    def maybeSendClose(c: Close): F[Unit] =
      F.delay(sentClose.compareAndSet(false, true)).flatMap { cond =>
        if (cond) writeFrame(c, trampoline)
        else F.unit
      } >> deadSignal.set(true)

    readFrameTrampoline
      .recoverWith {
        case t: ReservedOpcodeException =>
          F.delay(logger.error(t)("Decoded a websocket frame with a reserved opcode")) *>
            F.fromEither(Close(1003))
        case t: UnknownOpcodeException =>
          F.delay(logger.error(t)("Decoded a websocket frame with an unknown opcode")) *>
            F.fromEither(Close(1002))
        case t: ProtocolException =>
          F.delay(logger.error(t)("Websocket protocol violation")) *> F.fromEither(Close(1002))
      }
      .flatMap {
        case c: Close =>
          for {
            s <- F.delay(sentClose.get())
            //If we sent a close signal, we don't need to reply with one
            _ <- if (s) deadSignal.set(true) else maybeSendClose(c)
          } yield c
        case p @ Ping(d) =>
          //Reply to ping frame immediately
          writeFrame(Pong(d), trampoline) >> F.pure(p)
        case rest =>
          F.pure(rest)
      }
  }

  /** The websocket input stream
    *
    * Note: On receiving a close, we MUST send a close back, as stated in section
    * 5.5.1 of the websocket spec: https://tools.ietf.org/html/rfc6455#section-5.5.1
    *
    * @return
    */
  def inputstream: Stream[F, WebSocketFrame] =
    Stream.repeatEval(handleRead())

  //////////////////////// Startup and Shutdown ////////////////////////

  override protected def stageStartup(): Unit = {
    super.stageStartup()

    // Effect to send a close to the other endpoint
    val sendClose: F[Unit] = F.delay(closePipeline(None))

    val receiveSent: Stream[F, WebSocketFrame] =
      ws match {
        case WebSocketSeparatePipe(send, receive, _) =>
          //We don't need to terminate if the send stream terminates.
          send.concurrently(receive(inputstream))
        case WebSocketCombinedPipe(receiveSend, _) =>
          receiveSend(inputstream)
      }

    val wsStream =
      receiveSent
        .evalMap(snkFun)
        .drain
        .interruptWhen(deadSignal)
        .onFinalizeWeak(
          ws.onClose.attempt.void
        ) //Doing it this way ensures `sendClose` is sent no matter what
        .onFinalizeWeak(sendClose)
        .compile
        .drain

    val result = F.handleErrorWith(wsStream) {
      case EOF =>
        F.delay(stageShutdown())
      case t =>
        F.delay(logger.error(t)("Error closing Web Socket"))
    }
    dispatcher.unsafeRunAndForget(result)
  }

  override protected def stageShutdown(): Unit = {
    val fa = F.handleError(deadSignal.set(true)) { t =>
      logger.error(t)("Error setting dead signal")
    }
    dispatcher.unsafeRunAndForget(fa)
    super.stageShutdown()
  }
}

object Http4sWSStage {
  def bufferingSegment[F[_]](stage: Http4sWSStage[F]): LeafBuilder[WebSocketFrame] =
    TrunkBuilder(new SerializingStage[WebSocketFrame]).cap(stage)

  def apply[F[_]](
      ws: WebSocket[F],
      sentClose: AtomicBoolean,
      deadSignal: SignallingRef[F, Boolean],
      dispatcher: Dispatcher[F])(implicit F: Async[F]): F[Http4sWSStage[F]] =
    Semaphore[F](1L).map(t => new Http4sWSStage(ws, sentClose, deadSignal, t, dispatcher))
}
