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
import cats.syntax.all._
import fs2._
import fs2.concurrent.Channel
import org.http4s.Header
import org.http4s.Headers
import org.http4s.Message
import org.typelevel.log4cats.Logger
import scodec.bits._

import java.util.concurrent.CancellationException
import scala.annotation.nowarn

// Will eventually hold client/server through single interface matching that of the designed paradigm
// in StreamState
@nowarn("msg=implicit numeric widening")
private[h2] class H2Stream[F[_]: Concurrent](
    val id: Int,
    localSettings: H2Frame.Settings.ConnectionSettings,
    connectionType: H2Connection.ConnectionType,
    val remoteSettings: F[H2Frame.Settings.ConnectionSettings],
    val state: Ref[F, H2Stream.State[F]],
    val hpack: Hpack[F],
    val enqueue: cats.effect.std.Queue[F, Chunk[H2Frame]],
    val onClosed: F[Unit],
    val goAway: H2Error => F[Unit],
    private[this] val logger: Logger[F],
) {
  import H2Stream.StreamState

  def sendPushPromise(originating: Int, headers: NonEmptyList[(String, String, Boolean)]): F[Unit] =
    connectionType match {
      case H2Connection.ConnectionType.Server =>
        state.get.flatMap { s =>
          s.state match {
            case StreamState.Idle =>
              for {
                h <- hpack.encodeHeaders(headers)
                frame = H2Frame.PushPromise(originating, endHeaders = true, id, h, None)
                _ <- state.update(s => s.copy(state = StreamState.ReservedLocal))
                _ <- enqueue.offer(Chunk.singleton(frame))
              } yield ()
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
    maxFrameSize.flatMap(maxFrameSize =>
      mess.body
        .ifEmpty[F, Byte](
          Stream.exec(sendData(ByteVector.empty, endStream = true).whenA(noTrailers))
        )
        .chunkLimit(maxFrameSize)
        .zipWithNext
        .foreach { case (c, nextChunk) =>
          val isEndStream = nextChunk.isEmpty && noTrailers
          sendData(c.toByteVector, isEndStream)
        }
        .compile
        .drain
        .onError { case _ =>
          rstStream(H2Error.InternalError)
        }
    )
  }

  def sendTrailerHeaders(mess: Message[F]): F[Unit] =
    mess.attributes.lookup(Message.Keys.TrailerHeaders[F]) match {
      case None => Applicative[F].unit
      case Some(fhs) =>
        fhs.flatMap { hs =>
          hs.headers
            .map(a => (a.name.toString.toLowerCase(), a.value, false))
            .toNel
            .traverse_(sendHeaders(_, endStream = true))
        }
    }

  // TODO Check Settings to Split Headers into Headers and Continuation
  def sendHeaders(headers: NonEmptyList[(String, String, Boolean)], endStream: Boolean): F[Unit] =
    state.get.flatMap { s =>
      s.state match {
        case StreamState.Idle | StreamState.HalfClosedRemote | StreamState.Open |
            StreamState.ReservedLocal =>
          hpack.encodeHeaders(headers).flatMap { bv =>
            val f = H2Frame.Headers(id, None, endStream, endHeaders = true, bv, None)
            enqueue.offer(Chunk.singleton(f))
          } <*
            state
              .modify { b =>
                val newState: StreamState = (b.state, endStream) match {
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
                (b.copy(state = newState), newState)
              }
              .flatMap { state =>
                if (state == StreamState.Closed) onClosed else Applicative[F].unit
              }
        case _ => new IllegalStateException("Stream Was Closed").raiseError
      }
    }

  def sendData(bv: ByteVector, endStream: Boolean): F[Unit] = state.get.flatMap { s =>
    s.state match {
      case StreamState.Open | StreamState.HalfClosedRemote =>
        if (bv.size.toInt <= s.writeWindow && s.writeWindow > 0) {
          enqueue.offer(Chunk.singleton(H2Frame.Data(id, bv, None, endStream))) >> {
            state
              .modify { s =>
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
                  ),
                  newState,
                )
              }
              .flatMap(state => if (state == StreamState.Closed) onClosed else Applicative[F].unit)
          }
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
                enqueue.offer(Chunk.singleton(frame)) >> sendData(tail, endStream)
              }
          } else s.writeBlock.get.rethrow >> sendData(bv, endStream)
        }
      case _ => new IllegalStateException("Stream Was Closed").raiseError
    }
  }

  def receiveHeaders(headers: H2Frame.Headers, continuations: H2Frame.Continuation*): F[Unit] = {

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
          val block = headers.headerBlock ++ continuations.foldLeft(ByteVector.empty) {
            case (acc, cont) => acc ++ cont.headerBlockFragment
          }
          for {
            h <- hpack.decodeHeaders(block).onError { case e =>
              logger.error(e)(s"Issue in headers") >> goAway(H2Error.CompressionError)
            }
            newstate =
              if (headers.endStream) s.state match {
                case StreamState.Open => StreamState.HalfClosedRemote // Client
                case StreamState.Idle => StreamState.HalfClosedRemote // Server
                case StreamState.HalfClosedLocal => StreamState.Closed // Client
                case StreamState.ReservedRemote => StreamState.Closed
                case s => s
              }
              else
                s.state match {
                  case StreamState.Idle => StreamState.Open // Server
                  case StreamState.ReservedRemote => StreamState.HalfClosedLocal
                  case s => s
                }
            t <- state.modify(s => (s.copy(state = newstate), (s.request, s.response)))
            (request, response) = t
            _ <- connectionType match {
              case H2Connection.ConnectionType.Client =>
                response.tryGet.flatMap {
                  case x if x.isEmpty =>
                    PseudoHeaders.headersToResponseNoBody(h) match {
                      case Some(resp) =>
                        response.complete(Either.right(attribute(resp))) >>
                          checkLengthOf(resp) >>
                          (if (headers.endStream) s.readBuffer.close *> s.trailWith(List.empty).void
                           else Applicative[F].unit) >>
                          (if (newstate == StreamState.Closed) onClosed
                           else Applicative[F].unit)
                      case None =>
                        logger.error("Headers Unable to be parsed") >>
                          {rstStream(H2Error.ProtocolError)}
                    }
                  case _ =>
                    if (headers.endStream) s.readBuffer.close *> s.trailWith(h.toList).void
                    else s.trailWith(h.toList).void
                }
              case H2Connection.ConnectionType.Server =>
                request.tryGet.flatMap {
                  case x if x.isEmpty =>
                    PseudoHeaders.headersToRequestNoBody(h) match {
                      case Some(req) =>
                        request.complete(Either.right(attribute(req))) >>
                          checkLengthOf(req) >>
                          (if (headers.endStream) s.readBuffer.close *> s.trailWith(List.empty).void
                           else Applicative[F].unit) >>
                          (if (newstate == StreamState.Closed) onClosed
                           else Applicative[F].unit)
                      case None =>
                        logger.error("Headers Unable to be parsed") >>
                          rstStream(H2Error.ProtocolError)
                    }
                  case _ => s.trailWith(h.toList).void
                }
            }
          } yield ()
        case StreamState.HalfClosedRemote | StreamState.Closed =>
          goAway(H2Error.StreamClosed)
        case StreamState.ReservedLocal =>
          goAway(H2Error.ProtocolError)
      }
    }
  }

  def receivePushPromise(
      headers: H2Frame.PushPromise,
      continuations: H2Frame.Continuation*
  ): F[Unit] = state.get.flatMap { s =>
    connectionType match {
      case H2Connection.ConnectionType.Client =>
        s.state match {
          case StreamState.Idle =>
            val block = headers.headerBlock ++ continuations.foldLeft(ByteVector.empty) {
              case (acc, cont) => acc ++ cont.headerBlockFragment
            }
            for {
              h <- hpack.decodeHeaders(block).onError { case e =>
                logger.error(e)("Issue in headers"); goAway(H2Error.CompressionError)
              }
              _ <- state.update(s => s.copy(state = StreamState.ReservedRemote))
              _ <- PseudoHeaders.headersToRequestNoBody(h) match {
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
    s.state match {
      case StreamState.Open | StreamState.HalfClosedLocal =>
        import localSettings.initialWindowSize.windowSize

        val newSize = s.readWindow - data.data.size.toInt
        val newState = if (data.endStream) s.state match {
          case StreamState.Open => StreamState.HalfClosedRemote
          case StreamState.HalfClosedLocal => StreamState.Closed
          case s => s
        }
        else s.state

        val sizeReadOk = !data.endStream ||
          s.contentLengthCheck.forall { case (max, current) => max === (current + data.data.size) }

        val isClosed = newState == StreamState.Closed

        val needsWindowUpdate = newSize <= (windowSize / 2)
        for {
          _ <- state.update(s =>
            s.copy(
              state = newState,
              readWindow = if (needsWindowUpdate) windowSize else newSize,
              contentLengthCheck = s.contentLengthCheck.map { case (max, current) =>
                (max, current + data.data.size)
              },
            )
          )
          _ <-
            if (sizeReadOk) s.readBuffer.send(Right(data.data)).void
            else rstStream(H2Error.ProtocolError)

          _ <-
            if (needsWindowUpdate && !isClosed && sizeReadOk) {
              enqueue.offer(Chunk.singleton(H2Frame.WindowUpdate(id, windowSize - newSize)))
            } else Applicative[F].unit
          _ <-
            if (data.endStream) s.readBuffer.close *> s.trailWith(List.empty).void
            else Applicative[F].unit
          _ <-
            if (isClosed && sizeReadOk) onClosed else Applicative[F].unit
        } yield ()
      case StreamState.Idle =>
        goAway(H2Error.ProtocolError)
      case StreamState.HalfClosedRemote | StreamState.Closed =>
        rstStream(H2Error.StreamClosed)
      case StreamState.ReservedLocal | StreamState.ReservedRemote =>
        goAway(H2Error.InternalError) // Not Implemented Push promise Support
    }
  }

  def rstStream(error: H2Error): F[Unit] = {
    val rst = error.toRst(id)
    for {
      s <- state.modify(s => (s.copy(state = StreamState.Closed), s))
      _ <- enqueue.offer(Chunk.singleton(rst))
      _ <- s.cancelWith(s"Sending RstStream, cancelling: $rst")
      _ <- onClosed
    } yield ()
  }

  // Broadcast Frame
  // Will eventually allow us to know we can retry if we are above the processed window declared
  def receiveGoAway(goAway: H2Frame.GoAway): F[Unit] = for {
    s <- state.modify(s => (s.copy(state = StreamState.Closed), s))
    _ <- s.cancelWith(s"Received GoAway, cancelling: $goAway")
    _ <- onClosed
  } yield ()

  def receiveRstStream(rst: H2Frame.RstStream): F[Unit] = for {
    s <- state.modify(s => (s.copy(state = StreamState.Closed), s))
    _ <- s.cancelWith(s"Received RstStream, cancelling: $rst")
    _ <- onClosed
  } yield ()

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

  def readBody: Stream[F, Byte] = Stream.force(state.get.map(_.readBuffer.stream)).flatMap {
    case Right(bv) => Stream.chunk(Chunk.byteVector(bv))
    case Left(ex) => Stream.raiseError(ex)
  }

}

private[h2] object H2Stream {
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
  ) {
    override def toString: String =
      s"H2Stream.State(state=$state, writeWindow=$writeWindow, readWindow=$readWindow, contentLengthCheck=$contentLengthCheck)"

    private[h2] def trailWith(rawHs: List[(String, String)]): F[Boolean] = {
      val hs = Headers(rawHs.map(Header.ToRaw.keyValuesToRaw): _*)
      trailers.complete(Either.right(hs))
    }

    private[H2Stream] def cancelWith(msg: String)(implicit F: Monad[F]): F[Unit] = {
      // Unsure of this, but also unsure about exposing custom throwable
      val ex: Either[Throwable, Nothing] = Left(new CancellationException(msg))
      writeBlock.complete(ex) *>
        request.complete(ex) *>
        response.complete(ex) *>
        readBuffer.send(ex) *>
        trailers.complete(ex).void
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
