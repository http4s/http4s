/*
 * Copyright 2019 http4s.org
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

package org.http4s.ember.core.h2

import cats._
import cats.data._
import cats.effect._
import cats.effect.std.Semaphore
import cats.syntax.all._
import fs2._
import fs2.concurrent.Channel
import org.http4s.Header
import org.http4s.Headers
import org.http4s.Message
import org.http4s.Status
import org.typelevel.log4cats.Logger
import scodec.bits._

import java.util.concurrent.CancellationException
import scala.annotation.nowarn
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration

// Will eventually hold client/server through single interface matching that of the designed paradigm
// in StreamState
@nowarn("msg=implicit numeric widening")
private[h2] class H2Stream[F[_]: Temporal](
    val id: Int,
    sendTimeout: Duration,
    localSettings: H2Frame.Settings.ConnectionSettings,
    connectionType: H2Connection.ConnectionType,
    val remoteSettings: F[H2Frame.Settings.ConnectionSettings],
    val state: Ref[F, H2Stream.State[F]],
    private[this] val bodyDone: Deferred[F, Either[Throwable, Unit]],
    private[this] val readBufferLock: Semaphore[F],
    val enqueue: EncodableH2Frame => F[Unit],
    val onClosed: F[Unit],
    val goAway: H2Error => F[Unit],
    val creditConnection: Int => F[Unit],
    private[this] val logger: Logger[F],
) {
  import H2Stream.StreamState

  private[this] val windowSize: Int = localSettings.initialWindowSize.windowSize

  def sendPushPromise(originating: Int, headers: NonEmptyList[(String, String, Boolean)]): F[Unit] =
    connectionType match {
      case H2Connection.ConnectionType.Server =>
        state.get.flatMap { s =>
          s.state match {
            case StreamState.Idle =>
              Temporal[F]
                .uncancelable(poll =>
                  poll(
                    enqueue(
                      EncodableH2Frame.PushPromise(originating, endHeaders = true, id, headers)
                    )
                  ) *> setStreamState(_ => StreamState.ReservedLocal)
                )
            case _ =>
              new IllegalStateException(
                "Push Promises are only allowed on an idle Stream"
              ).raiseError
          }
        }
      case H2Connection.ConnectionType.Client =>
        new IllegalStateException("Clients Are Not Allowed To Send PushPromises").raiseError
    }

  /** Send [[Message]] in chunks according to remote receiver's max frame size.
    * On empty [[Message]], send an empty DATA frame if there are no trailer headers.
    *
    * @param mess the [[Message]] to send
    */
  def sendMessageBody(mess: Message[F]): F[Unit] = {
    val noTrailers = !mess.attributes.contains(Message.Keys.TrailerHeaders[F])
    val maxFrameSize = remoteSettings.map(_.maxFrameSize.frameSize)
    maxFrameSize.flatMap { maxFrameSize =>
      val sendBody =
        mess.body
          .chunkLimit(maxFrameSize)
          .foreach(c => sendData(c.toByteVector, endStream = false))
          .compile
          .drain >> sendData(ByteVector.empty, endStream = true).whenA(noTrailers)
      sendBody.onError { case _ =>
        rstStream(H2Error.InternalError)
      }
    }
  }

  def sendTrailerHeaders(mess: Message[F]): F[Unit] =
    mess.attributes.lookup(Message.Keys.TrailerHeaders[F]) match {
      case None => Applicative[F].unit
      case Some(fhs) =>
        fhs.flatMap { hs =>
          hs.headers
            .map(a => (a.name.toString.toLowerCase(), a.value, false))
            .toNel
            .fold(sendData(ByteVector.empty, endStream = true))(sendHeaders(_, endStream = true))
        }
    }

  // TODO Check Settings to Split Headers into Headers and Continuation
  def sendHeaders(headers: NonEmptyList[(String, String, Boolean)], endStream: Boolean): F[Unit] =
    state.get.flatMap { s =>
      s.state match {
        case StreamState.Idle | StreamState.HalfClosedRemote | StreamState.Open |
            StreamState.ReservedLocal =>
          Temporal[F]
            .uncancelable(poll =>
              poll(
                enqueue(EncodableH2Frame.Headers(id, None, endStream, endHeaders = true, headers))
              ) *>
                setStreamState { st =>
                  (st, endStream) match {
                    case (StreamState.Idle, false) => StreamState.Open
                    case (StreamState.Idle, true) => StreamState.HalfClosedLocal
                    case (StreamState.HalfClosedRemote, false) => StreamState.HalfClosedRemote
                    case (StreamState.HalfClosedRemote, true) => StreamState.Closed
                    case (StreamState.Open, false) => StreamState.Open
                    case (StreamState.Open, true) => StreamState.HalfClosedLocal
                    case (StreamState.ReservedLocal, true) => StreamState.Closed
                    case (StreamState.ReservedLocal, false) => StreamState.HalfClosedRemote
                    case (st, _) => st // Hopefully Impossible
                  }
                }
            )
        case _ => new IllegalStateException("Stream Was Closed").raiseError
      }
    }

  def sendData(bv: ByteVector, endStream: Boolean): F[Unit] = state.get.flatMap { s =>
    s.state match {
      case StreamState.Open | StreamState.HalfClosedRemote =>
        if (bv.size.toInt <= s.writeWindow && s.writeWindow > 0) {
          enqueue(H2Frame.Data(id, bv, None, endStream)) >>
            transitionState { s =>
              val newState = if (endStream) {
                s.state match {
                  case StreamState.Open => StreamState.HalfClosedLocal
                  case StreamState.HalfClosedRemote => StreamState.Closed
                  case st => st // Ruh-roh
                }
              } else s.state
              (
                s.copy(
                  state = newState,
                  writeWindow = s.writeWindow - bv.size.toInt,
                  stallStart = None,
                ),
                (),
              )
            }((_, _) => Applicative[F].unit)
        } else {
          if (s.writeWindow > 0) {
            state
              .modify { s =>
                val head = bv.take(s.writeWindow)
                val tail = bv.drop(s.writeWindow)
                (s.copy(writeWindow = s.writeWindow - head.size.toInt), (head, tail))
              }
              .flatMap { case (head, tail) =>
                val frame = H2Frame.Data(id, head, None, endStream = false)
                enqueue(frame) >> sendData(tail, endStream)
              }
          } else {
            Temporal[F].monotonic
              .flatMap(now =>
                state.modify { st =>
                  val start = st.stallStart.getOrElse(now)
                  (st.copy(stallStart = Some(start)), now - start)
                }
              )
              .flatMap { elapsed =>
                val remaining = sendTimeout - elapsed
                if (remaining <= Duration.Zero)
                  logger.debug(s"stream stall timeout exceeded ($elapsed)") >>
                    rstStream(H2Error.ProtocolError)
                else
                  Temporal[F].timeout(s.writeBlock.get.rethrow, remaining).attempt.flatMap[Unit] {
                    case Right(_) => sendData(bv, endStream)
                    case Left(_: java.util.concurrent.TimeoutException) =>
                      logger.debug(s"stream stall timeout exceeded") >>
                        rstStream(H2Error.ProtocolError)
                    case Left(e) => e.raiseError
                  }
              }
          }
        }
      case _ => new IllegalStateException("Stream Was Closed").raiseError
    }
  }

  def receiveHeaders(
      headers: DecodedH2Frame.Headers
  ): F[Unit] = {

    def checkLengthOf(mess: Message[Pure]): F[Unit] =
      mess.contentLength.traverse_ { length =>
        state.update(s => s.copy(contentLengthCheck = Some((length, 0))))
      }

    state.get.flatMap { s =>
      def attribute(mess: Message[Pure]): mess.Self = {
        val iMess = mess.withAttribute(H2Keys.StreamIdentifier, id)
        val trailerF = s.trailers.get.rethrow
        iMess.withAttribute(org.http4s.Message.Keys.TrailerHeaders[F], trailerF)
      }

      s.state match {
        case StreamState.Open | StreamState.HalfClosedLocal | StreamState.Idle |
            StreamState.ReservedRemote =>
          // Run first in each valid branch, before completing the request/response.
          // Not in the malformed branch: if this frame would close the stream,
          // rstStream must still see it open so it can reset it.
          val setState = (rest: F[Unit]) =>
            transitionState { current =>
              val newState =
                if (headers.endStream) current.state match {
                  case StreamState.Open => StreamState.HalfClosedRemote // Client
                  case StreamState.Idle => StreamState.HalfClosedRemote // Server
                  case StreamState.HalfClosedLocal => StreamState.Closed // Client
                  case StreamState.ReservedRemote => StreamState.Closed
                  case st => st
                }
                else
                  current.state match {
                    case StreamState.Idle => StreamState.Open // Server
                    case StreamState.ReservedRemote => StreamState.HalfClosedLocal
                    case st => st
                  }
              (current.copy(state = newState), ())
            }((_, poll) => poll(rest))
          for {
            _ <- connectionType match {
              case H2Connection.ConnectionType.Client =>
                s.response.tryGet.flatMap {
                  case x if x.isEmpty =>
                    PseudoHeaders.headersToResponseNoBody(headers.headers) match {
                      // RFC 9113 8.1: skip 1xx until the final response, 1xx with END_STREAM is malformed
                      // RFC 9113 8.6: 101 is not supported in HTTP/2
                      case Some(resp) if resp.status.responseClass == Status.Informational =>
                        if (resp.status == Status.SwitchingProtocols || headers.endStream)
                          logger.debug(s"Malformed informational response ${resp.status}") >>
                            rstStream(H2Error.ProtocolError)
                        else Applicative[F].unit
                      case Some(resp) =>
                        setState(
                          s.response.complete(Either.right(attribute(resp))) >>
                            checkLengthOf(resp) >>
                            (if (headers.endStream)
                               s.readBuffer.close *> s.trailWith(List.empty).void
                             else Applicative[F].unit)
                        )
                      case None =>
                        logger.error("Headers Unable to be parsed") >>
                          rstStream(H2Error.ProtocolError)
                    }
                  case _ =>
                    setState(
                      s.readBuffer.close.whenA(headers.endStream) >>
                        s.trailWith(headers.headers.toList).void
                    )
                }
              case H2Connection.ConnectionType.Server =>
                s.request.tryGet.flatMap {
                  case x if x.isEmpty =>
                    PseudoHeaders.headersToRequestNoBody(headers.headers) match {
                      case Some(req) =>
                        setState(
                          s.request.complete(Either.right(attribute(req))) >>
                            checkLengthOf(req) >>
                            (if (headers.endStream)
                               s.readBuffer.close *> s.trailWith(List.empty).void
                             else Applicative[F].unit)
                        )
                      case None =>
                        logger.error("Headers Unable to be parsed") >>
                          rstStream(H2Error.ProtocolError)
                    }
                  case _ =>
                    setState(
                      s.readBuffer.close.whenA(headers.endStream) >>
                        s.trailWith(headers.headers.toList).void
                    )
                }
            }
          } yield ()
        case StreamState.HalfClosedRemote =>
          goAway(H2Error.StreamClosed)
        case StreamState.Closed if s.remoteReset =>
          logger.debug(s"Received Headers for Remote Closed Stream $id - Terminating") >>
            goAway(H2Error.ProtocolError)
        case StreamState.Closed =>
          logger.debug(s"Received Headers for Closed Stream $id - Ignoring")
        case StreamState.ReservedLocal =>
          goAway(H2Error.ProtocolError)
      }
    }
  }

  def receivePushPromise(
      headers: DecodedH2Frame.PushPromise
  ): F[Unit] = state.get.flatMap { s =>
    connectionType match {
      case H2Connection.ConnectionType.Client =>
        s.state match {
          case StreamState.Idle =>
            for {
              _ <- setStreamState(_ => StreamState.ReservedRemote)
              _ <- PseudoHeaders.headersToRequestNoBody(headers.headers) match {
                case Some(req) =>
                  val attributed =
                    req
                      .withAttribute(H2Keys.StreamIdentifier, id)
                      .withAttribute(H2Keys.PushPromiseInitialStreamIdentifier, headers.identifier)
                  s.request.complete(Either.right(attributed)).void
                case None => rstStream(H2Error.ProtocolError)
              }
            } yield ()

          case _ => goAway(H2Error.ProtocolError)
        }
      case H2Connection.ConnectionType.Server =>
        goAway(H2Error.ProtocolError)
    }
  }

  def receiveData(data: H2Frame.Data): F[Unit] = state.get.flatMap { s =>
    val awaitingResponse = connectionType match {
      case H2Connection.ConnectionType.Client => s.response.tryGet.map(_.isEmpty)
      case H2Connection.ConnectionType.Server => false.pure[F]
    }
    awaitingResponse.flatMap(receiveData(data, s, _))
  }

  private def receiveData(
      data: H2Frame.Data,
      s: H2Stream.State[F],
      awaitingResponse: Boolean,
  ): F[Unit] = {
    val size = data.flowControlSize
    s.state match {
      // RFC 9113 8.1, 8.1.1: DATA before the final response HEADERS is malformed
      case StreamState.Open | StreamState.HalfClosedLocal if awaitingResponse =>
        logger.debug(s"Received Data before response headers on stream $id") >>
          creditConnection(size) >> rstStream(H2Error.ProtocolError)
      case StreamState.Open | StreamState.HalfClosedLocal =>
        val bytes = data.data.size.toInt
        transitionState { s =>
          val newState = if (data.endStream) s.state match {
            case StreamState.Open => StreamState.HalfClosedRemote
            case StreamState.HalfClosedLocal => StreamState.Closed
            case st => st
          }
          else s.state

          val sizeReadOk = !data.endStream ||
            s.contentLengthCheck.forall { case (max, current) =>
              max === (current + data.data.size)
            }

          if (size > s.readWindow) (s, Option[H2Error](H2Error.FlowControlError))
          else if (!sizeReadOk) (s, Option[H2Error](H2Error.ProtocolError))
          else
            (
              s.copy(
                state = newState,
                readWindow = s.readWindow - size,
                advertisedReadWindow = s.advertisedReadWindow - size,
                unreadBytes = s.unreadBytes + bytes,
                contentLengthCheck = s.contentLengthCheck.map { case (max, current) =>
                  (max, current + data.data.size)
                },
              ),
              Option.empty[H2Error],
            )
        } {
          case (Some(error), poll) => creditConnection(size) >> poll(rstStream(error))
          case (None, poll) =>
            for {
              _ <- creditConnection(size - bytes).whenA(data.pad.isDefined)
              // DATA slices can retain an entire socket read, including unrelated frames.
              // Copy the payload so retained storage follows the receive-window charge.
              accepted <-
                if (bytes > 0) poll(s.readBuffer.send(Right(data.data.copy)))
                else Right(()).pure[F]
              _ <- discardUnread(bytes).whenA(accepted.isLeft)
              _ <- (s.readBuffer.close *> s.trailWith(List.empty)).whenA(data.endStream)
            } yield ()
        }
      case StreamState.Idle =>
        goAway(H2Error.ProtocolError)
      case StreamState.HalfClosedRemote =>
        creditConnection(size) >> rstStream(H2Error.StreamClosed)
      case StreamState.Closed if s.remoteReset =>
        logger.debug(s"Received Data for Remote Closed Stream $id - Terminating") >>
          goAway(H2Error.ProtocolError)
      case StreamState.Closed =>
        logger.debug(s"Received Data for Closed Stream $id - Ignoring") >> creditConnection(size)
      case StreamState.ReservedLocal | StreamState.ReservedRemote =>
        goAway(H2Error.InternalError) // Not Implemented Push promise Support
    }
  }

  /** Transfers up to `n` bytes from body accounting to connection credit without
    * allowing cancellation between the two updates. Crediting never waits for socket I/O.
    */
  private def discardUnread(n: Int): F[Unit] =
    state.flatModify { s =>
      val credited = math.min(n, s.unreadBytes)
      (
        s.copy(unreadBytes = s.unreadBytes - credited),
        creditConnection(credited).whenA(credited > 0),
      )
    }

  private def discardUnread: F[Unit] = discardUnread(Int.MaxValue)

  /** Only the connection's credit sender waits on the outgoing queue. An abandoned
    * grant needs no rollback: either this stream or the entire sender is terminating.
    */
  private[h2] def sendReadWindowUpdate: F[Unit] =
    state
      .modify { s =>
        val pending = windowSize - s.readWindow - s.unreadBytes
        val grant = s.state match {
          case StreamState.Open | StreamState.HalfClosedLocal if pending >= windowSize / 2 =>
            pending
          case _ => 0
        }
        (s.copy(readWindow = s.readWindow + grant), (grant, s.trailers))
      }
      .flatMap { case (grant, trailers) =>
        Temporal[F]
          .race(enqueue(H2Frame.WindowUpdate(id, grant)), trailers.get)
          .void
          .whenA(grant > 0)
      }

  /** The channel permits only one consumer. An active reader performs this
    * cleanup in its finalizer, so resetting a stream never waits for that reader.
    * Refund the remaining charge only after both the reader and buffer are gone.
    */
  private def discardBody(s: H2Stream.State[F]): F[Unit] =
    Temporal[F].uncancelable { _ =>
      s.readBuffer.close >> readBufferLock.tryPermit.use { acquired =>
        (s.readBuffer.stream.compile.drain >> discardUnread).whenA(acquired)
      }
    }

  private def cancelBody(s: H2Stream.State[F], message: String): F[Unit] =
    Temporal[F].uncancelable { _ =>
      val ex: Either[Throwable, Nothing] = Left(new CancellationException(message))
      s.writeBlock.complete(ex) *>
        s.request.complete(ex) *>
        s.response.complete(ex) *>
        s.trailers.complete(ex).flatMap { discard =>
          // The first cancellation owns cleanup; preserve successfully completed bodies.
          bodyDone.complete(ex).whenA(discard) *>
            s.readBuffer.close *>
            discardBody(s).whenA(discard)
        }
    }

  /** Finishes local processing, discards the body, and resets the stream unless
    * it is already closed.
    */
  def finish(error: H2Error): F[Unit] =
    Temporal[F].uncancelable { _ =>
      state.get.flatMap(s => bodyDone.complete(Either.unit) >> discardBody(s))
    } >> rstStream(error)

  /** Applies `f` to the current state, then runs `after` uncancelably unless it polls.
    * A Closed stream stays Closed. On the edge into Closed, `onClosed` runs once `after`
    * ends, even if it fails.
    */
  private def transitionState[A, B](
      f: H2Stream.State[F] => (H2Stream.State[F], A)
  )(after: (A, Poll[F]) => F[B]): F[B] =
    state.flatModifyFull { (poll, s) =>
      val (next, a) = f(s)
      if (s.state == StreamState.Closed) (next.copy(state = StreamState.Closed), after(a, poll))
      else if (next.state == StreamState.Closed)
        (next, Temporal[F].guarantee(after(a, poll), onClosed))
      else (next, after(a, poll))
    }

  private def setStreamState(f: StreamState => StreamState): F[Unit] =
    transitionState(s => (s.copy(state = f(s.state)), ()))((_, _) => Applicative[F].unit)

  private def closeOnce(message: String, remoteReset: Boolean)(andThen: F[Unit]): F[Unit] =
    transitionState { s =>
      (
        s.copy(state = StreamState.Closed, remoteReset = s.remoteReset || remoteReset),
        if (s.state == StreamState.Closed) None else Some(s),
      )
    } {
      case (Some(s), poll) => cancelBody(s, message).attempt.flatTap(_ => poll(andThen)).rethrow
      case (None, _) => Applicative[F].unit
    }

  def rstStream(error: H2Error): F[Unit] = {
    val rst = error.toRst(id)
    closeOnce(s"Sending RstStream, cancelling: $rst", remoteReset = false)(enqueue(rst))
  }

  // Broadcast Frame
  // Will eventually allow us to know we can retry if we are above the processed window declared
  def receiveGoAway(goAway: H2Frame.GoAway): F[Unit] =
    closeOnce(s"Received GoAway, cancelling: $goAway", remoteReset = false)(Applicative[F].unit)

  def connectionClosed: F[Unit] =
    closeOnce("Connection closed, cancelling", remoteReset = false)(Applicative[F].unit).void

  def receiveRstStream(rst: H2Frame.RstStream): F[Unit] =
    closeOnce(s"Received RstStream, cancelling: $rst", remoteReset = true)(Applicative[F].unit)

  // Important for telling folks we can send more data
  def receiveWindowUpdate(window: H2Frame.WindowUpdate): F[Unit] = for {
    newWriteBlock <- Deferred[F, Either[Throwable, Unit]]
    t <- state.modify { s =>
      val oldSize = s.writeWindow
      val newSize = oldSize + window.windowSizeIncrement
      val sizeValid = (s.writeWindow >= 0 && newSize >= 0) || s.writeWindow < 0 // Less than 2^31-1
      val newS = s.copy(writeBlock = newWriteBlock, writeWindow = newSize)
      // println(s"Receive Window Update $newS - increment: ${window.windowSizeIncrement} oldSize: $oldSize")
      (newS, (s.writeBlock, sizeValid))
    }
    (oldWriteBlock, valid) = t

    _ <- {
      if (!valid) rstStream(H2Error.FlowControlError)
      else oldWriteBlock.complete(Either.unit).void
    }
  } yield ()

  def modifyWriteWindow(amount: Int): F[Unit] = for {
    newWriteBlock <- Deferred[F, Either[Throwable, Unit]]
    oldWriteBlock <- state.modify { s =>
      val newSize = s.writeWindow + amount
      val newS = s.copy(writeBlock = newWriteBlock, writeWindow = newSize)
      // println(s"Modify Write Window $newS")
      (newS, s.writeBlock)
    }

    _ <- oldWriteBlock.complete(Either.unit).void
  } yield ()

  def getRequest: F[org.http4s.Request[fs2.Pure]] = state.get.flatMap(_.request.get.rethrow)
  def getResponse: F[org.http4s.Response[fs2.Pure]] = state.get.flatMap(_.response.get.rethrow)

  def readBody: Stream[F, Byte] = Stream.force(state.get.map { s =>
    Stream
      .resource(readBufferLock.permit)
      .flatMap { _ =>
        s.readBuffer.stream.evalMap {
          case Right(bv) => discardUnread(bv.size.toInt).as(Chunk.byteVector(bv))
          case Left(ex) => ex.raiseError[F, Chunk[Byte]]
        }.unchunks
      }
      .scope
      .onFinalize(bodyDone.tryGet.flatMap(done => discardBody(s).whenA(done.isDefined)))
      .interruptWhen(bodyDone) ++
      Stream.exec(s.trailers.get.rethrow.void)
  })

}

private[h2] object H2Stream {

  /** Internal state for a stream. Unlike the H2Stream members themselves, these values may change
    * over the lifespan of the stream.
    *
    * @param remoteReset flag to indicate if the stream was reset by the remote
    */
  final case class State[F[_]](
      state: StreamState,
      writeWindow: Int,
      writeBlock: Deferred[F, Either[Throwable, Unit]],
      readWindow: Int,
      request: Deferred[F, Either[Throwable, org.http4s.Request[fs2.Pure]]],
      response: Deferred[F, Either[Throwable, org.http4s.Response[fs2.Pure]]],
      trailers: Deferred[F, Either[Throwable, org.http4s.Headers]],
      readBuffer: Channel[F, Either[Throwable, ByteVector]],
      contentLengthCheck: Option[(Long, Long)],
      stallStart: Option[FiniteDuration],
      unreadBytes: Int,
      advertisedReadWindow: Long,
      remoteReset: Boolean,
  ) {
    override def toString: String =
      s"H2Stream.State(state=$state, writeWindow=$writeWindow, readWindow=$readWindow, contentLengthCheck=$contentLengthCheck)"

    private[h2] def trailWith(rawHs: List[(String, String)]): F[Boolean] = {
      val hs = Headers(rawHs.map(Header.ToRaw.keyValuesToRaw): _*)
      trailers.complete(Either.right(hs))
    }

    def isClosed: Boolean = state == StreamState.HalfClosedRemote || state == StreamState.Closed

  }

  sealed trait StreamState
  object StreamState {
    /*
                                  +--------+
                          send PP |        | recv PP
                        ,--------|  idle  |--------.
                        /         |        |         \
                      v          +--------+          v
                +----------+          |           +----------+
                |          |          | send H /  |          |
        ,------| reserved |          | recv H    | reserved |------.
        |      | (local)  |          |           | (remote) |      |
        |      +----------+          v           +----------+      |
        |          |             +--------+             |          |
        |          |     recv ES |        | send ES     |          |
        |   send H |     ,-------|  open  |-------.     | recv H   |
        |          |    /        |        |        \    |          |
        |          v   v         +--------+         v   v          |
        |      +----------+          |           +----------+      |
        |      |   half   |          |           |   half   |      |
        |      |  closed  |          | send R /  |  closed  |      |
        |      | (remote) |          | recv R    | (local)  |      |
        |      +----------+          |           +----------+      |
        |           |                |                 |           |
        |           | send ES /      |       recv ES / |           |
        |           | send R /       v        send R / |           |
        |           | recv R     +--------+   recv R   |           |
        | send R /  `----------->|        |<-----------'  send R / |
        | recv R                 | closed |               recv R   |
        `----------------------->|        |<----------------------'
                                  +--------+

            send:   endpoint sends this frame
            recv:   endpoint receives this frame

            H:  HEADERS frame (with implied CONTINUATIONs)
            PP: PUSH_PROMISE frame (with implied CONTINUATIONs)
            ES: END_STREAM flag
            R:  RST_STREAM frame
     */

    case object Idle extends StreamState // Transition to ReservedLocal/ReservedRemote/Open
    case object ReservedLocal extends StreamState // Transition to HalfClosedRemote/Closed
    case object ReservedRemote extends StreamState // Transition to HalfClosedLocal/Closed
    case object Open extends StreamState // Transition to HalfClosedRemote/HalfClosedLocal/Closed
    case object HalfClosedRemote extends StreamState // Transition to Closed
    case object HalfClosedLocal extends StreamState // Transition to Closed
    case object Closed extends StreamState // Terminal

  }
}
