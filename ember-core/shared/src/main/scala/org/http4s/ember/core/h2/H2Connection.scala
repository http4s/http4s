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
import cats.effect._
import cats.effect.kernel.Outcome
import cats.effect.std.Semaphore
import cats.effect.syntax.all._
import cats.syntax.all._
import com.comcast.ip4s.GenSocketAddress
import fs2._
import fs2.concurrent.Channel
import fs2.concurrent.SignallingRef
import fs2.io.net.Socket
import org.http4s.ember.core.h2.H2Connection.ContinuationProgress
import org.typelevel.log4cats.Logger
import scodec.bits._

import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration

import H2Frame.Settings.SettingsInitialWindowSize

private[h2] class H2Connection[F[_]](
    address: GenSocketAddress,
    connectionType: H2Connection.ConnectionType,
    receiveHeadersTimeout: Duration,
    idleTimeout: Duration,
    localSettings: H2Frame.Settings.ConnectionSettings,
    val mapRef: Ref[F, Map[Int, H2Stream[F]]],
    val state: Ref[F, H2Connection.State[F]], // odd if client, even if server
    val pendingReadCredit: SignallingRef[F, Int],
    val writeWindow: H2Connection.WriteWindow[F],
    outgoingQueue: cats.effect.std.Queue[F, H2Frame],
    val createdStreams: cats.effect.std.Queue[F, Int],
    val closedStreams: cats.effect.std.Queue[F, Int],
    hpack: Hpack[F],
    val streamCreateAndHeaders: Resource[F, Unit],
    val settingsAck: Deferred[F, Either[Throwable, H2Frame.Settings.ConnectionSettings]],
    acc: ByteVector, // Any Bytes Already Read
    socket: Socket[F],
    logger: Logger[F],
)(implicit F: Temporal[F]) {

  private[this] def addrStr = address.toString

  private[this] val maxHeaderBlockSize: Long =
    localSettings.maxHeaderListSize.fold(65536L)(_.listSize.toLong)

  private[this] val readIdleTimeout: Duration = connectionType match {
    case H2Connection.ConnectionType.Server => idleTimeout
    case H2Connection.ConnectionType.Client => Duration.Inf
  }

  /** Whether the peer is waiting for local response handling or receive-window credit.
    */
  private[this] def peerAwaitingUs: F[Boolean] =
    (state.get, mapRef.get.flatMap(_.values.toList.traverse(_.state.get))).mapN {
      (connection, streams) =>
        streams.exists { stream =>
          stream.state match {
            case H2Stream.StreamState.Idle | H2Stream.StreamState.ReservedLocal |
                H2Stream.StreamState.HalfClosedRemote =>
              true
            case H2Stream.StreamState.Open | H2Stream.StreamState.HalfClosedLocal =>
              connection.advertisedReadWindow <= 0 || stream.advertisedReadWindow <= 0
            case _ => false
          }
        }
    }

  /** Whether a frame for a stream missing from the map is for one we have
    * already closed and forgotten rather than one that was never opened.
    */
  private[this] def isClosedStream(id: Int, st: H2Connection.State[F]): Boolean = {
    val remoteParity = connectionType match {
      case H2Connection.ConnectionType.Server => id % 2 != 0
      case H2Connection.ConnectionType.Client => id % 2 == 0
    }
    id > 0 && (if (remoteParity) id <= st.remoteHighestStream else id <= st.highestStream)
  }

  // An unauthenticated peer can open streams without limit.  The 4x
  // gives us slack to reap the closed streams in a graceful fashion,
  // while giving a hard upper bound to protect the server or client
  // in case of abuse.
  private[this] val maxConcurrentRemoteStreams: Long =
    localSettings.maxConcurrentStreams.maxConcurrency.intValue.toLong * 4L

  def initiateLocalStream: F[H2Stream[F]] = for {
    t <- state.modify { s =>
      val highestIsEven = s.highestStream % 2 == 0
      val newHighest = connectionType match {
        case H2Connection.ConnectionType.Server =>
          if (highestIsEven) s.highestStream + 2 else s.highestStream + 1
        case H2Connection.ConnectionType.Client =>
          if (highestIsEven) s.highestStream + 1 else s.highestStream + 2
      }
      (s.copy(highestStream = newHighest), (s.remoteSettings, newHighest))
    }
    (settings, id) = t

    writeBlock <- Deferred[F, Either[Throwable, Unit]]
    request <- Deferred[F, Either[Throwable, org.http4s.Request[fs2.Pure]]]
    response <- Deferred[F, Either[Throwable, org.http4s.Response[fs2.Pure]]]
    trailers <- Deferred[F, Either[Throwable, org.http4s.Headers]]
    body <- Channel.unbounded[F, Either[Throwable, ByteVector]]
    bodyDone <- Deferred[F, Either[Throwable, Unit]]
    readBufferLock <- Semaphore[F](1)
    refState <- Ref.of[F, H2Stream.State[F]](
      H2Stream.State(
        H2Stream.StreamState.Idle,
        settings.initialWindowSize.windowSize,
        writeBlock,
        localSettings.initialWindowSize.windowSize,
        request,
        response,
        trailers,
        body,
        None,
        None,
        0,
        localSettings.initialWindowSize.windowSize.toLong,
        remoteReset = false,
      )
    )
    stream = new H2Stream(
      id,
      idleTimeout,
      localSettings,
      connectionType,
      state.get.map(_.remoteSettings),
      refState,
      bodyDone,
      readBufferLock,
      hpack,
      offerFrame,
      closedStreams.offer(id),
      goAway,
      creditReadWindow,
      logger,
    )
    _ <- mapRef.update(m => m + (id -> stream))
  } yield stream

  def initiateRemoteStreamById(id: Int): F[H2Stream[F]] = for {
    openStreams <- mapRef.get.map(_.size)
    _ <-
      if (openStreams >= maxConcurrentRemoteStreams)
        logger.debug(
          s"Open remote streams ($openStreams) at concurrency ceiling: issuing GoAway"
        ) >> goAway(H2Error.EnhanceYourCalm)
      else F.unit
    t <- state.get.map(s => (s.remoteSettings, s.remoteHighestStream))
    (settings, _) = t
    writeBlock <- Deferred[F, Either[Throwable, Unit]]
    request <- Deferred[F, Either[Throwable, org.http4s.Request[fs2.Pure]]]
    response <- Deferred[F, Either[Throwable, org.http4s.Response[fs2.Pure]]]
    trailers <- Deferred[F, Either[Throwable, org.http4s.Headers]]
    body <- Channel.unbounded[F, Either[Throwable, ByteVector]]
    bodyDone <- Deferred[F, Either[Throwable, Unit]]
    readBufferLock <- Semaphore[F](1)
    refState <- Ref.of[F, H2Stream.State[F]](
      H2Stream.State(
        H2Stream.StreamState.Idle,
        settings.initialWindowSize.windowSize,
        writeBlock,
        localSettings.initialWindowSize.windowSize,
        request,
        response,
        trailers,
        body,
        None,
        None,
        0,
        localSettings.initialWindowSize.windowSize.toLong,
        remoteReset = false,
      )
    )
    stream = new H2Stream(
      id,
      idleTimeout,
      localSettings,
      connectionType,
      state.get.map(_.remoteSettings),
      refState,
      bodyDone,
      readBufferLock,
      hpack,
      offerFrame,
      closedStreams.offer(id),
      goAway,
      creditReadWindow,
      logger,
    )
    _ <- mapRef.update(m => m + (id -> stream))
    _ <- state.update(s =>
      s.copy(
        highestStream = Math.max(s.highestStream, id),
        remoteHighestStream = Math.max(s.remoteHighestStream, id),
      )
    )
  } yield stream

  /** Records consumed or discarded bytes without waiting for the outgoing queue.
    * Every positive credit wakes the sender: a stream grant may be due even when the
    * connection has not yet accumulated half a window of credit.
    */
  def creditReadWindow(n: Int): F[Unit] =
    pendingReadCredit.update(_ + n).whenA(n > 0)

  /** One sender per connection coalesces connection and stream grants.
    * It is canceled with the write loop, so an offer cannot hold up connection shutdown.
    */
  private def sendReadWindowUpdates: Stream[F, Nothing] =
    // Equal observations can represent new credit after a grant; do not use changes.
    pendingReadCredit.discrete.filter(_ > 0).foreach { pending =>
      // Only this sender subtracts credit; preserve additions since this notification.
      // Skip updates below the threshold so the sender does not notify itself forever.
      (pendingReadCredit.update(_ - pending) >>
        state.update(s => s.copy(readWindow = s.readWindow + pending)) >>
        offerFrame(H2Frame.WindowUpdate(0, pending)))
        .whenA(pending >= localSettings.initialWindowSize.windowSize / 2) >>
        mapRef.get.flatMap(_.values.toList.traverse_(_.sendReadWindowUpdate))
    }

  def goAway(error: H2Error): F[Unit] =
    state.get.map(_.remoteHighestStream).flatMap { i =>
      val g = error.toGoAway(i)
      offerFrame(g)
    } >>
      H2Connection.KillWithoutMessage().raiseError

  // Terminate the connection. Skips over the write queue, as it may be
  // triggered by a stall in writeChunk to begin with.
  def goAwayImmediately(error: H2Error): F[Unit] =
    state.get.map(_.remoteHighestStream).flatMap { i =>
      val g = error.toGoAway(i)
      // Last-ditch timeout in case TCP layer is stalled.
      Temporal[F]
        .timeout(
          socket.write(Chunk.byteVector(H2Frame.toByteVector(g))),
          idleTimeout,
        )
        .recoverWith { case e: Throwable => logger.warn(e)("Failed to send GOAWAY") } >>
        closeWithGoAway(g)
    } >> H2Connection.KillWithoutMessage().raiseError

  private def closeWithGoAway(goAway: H2Frame.GoAway) =
    mapRef.get.flatMap(m => m.values.toList.traverse_(stream => stream.receiveGoAway(goAway))) >>
      state.update(s => s.copy(closed = true))

  def offerFrame(frame: H2Frame): F[Unit] =
    frame match {
      case data: H2Frame.Data =>

        /*
         * When sending a data frame, send as much as possible in the current window, splitting the
         * frame if needed, before blocking to wait.
         *
         * Offering the frame to the outgoing queue has to be cancelable, as it is possible for the
         * queue to get filled and never drain if the connection stalls out. The flatModifyFull
         * ensures cancelation never happens before the refundWindow cancelation handler is set up.
         */
        def go(data: H2Frame.Data): F[Unit] =
          F.bracketFull(poll => poll(writeWindow.take(data.flowControlSize))) { available =>
            if (available == data.flowControlSize)
              outgoingQueue.offer(data).as(Option.empty[H2Frame.Data])
            else if (available > 0) {
              val (head, tail) = data.data.splitAt(available.toLong)
              val headFrame = data.copy(data = head, pad = None, endStream = false)
              val tailFrame = data.copy(data = tail)
              outgoingQueue.offer(headFrame).as(tailFrame.some)
            } else
              F.pure(data.some)
          } {
            case (_, Outcome.Succeeded(_)) => F.unit
            case (available, _) =>
              writeWindow.change(available).ifM(F.unit, goAway(H2Error.FlowControlError))
          }.flatMap {
            case Some(remaining) => go(remaining)
            case None => F.unit
          }

        withStallTimeout(go(data))
      case other: H2Frame => outgoingQueue.offer(other)
    }

  private[this] def withStallTimeout[A](fa: F[Unit]): F[Unit] =
    Temporal[F].monotonic
      .flatMap { now =>
        state
          .modify { st =>
            val start = st.stallStart.getOrElse(now)
            (st.copy(stallStart = Some(start)), now - start)
          }
          .flatMap { elapsed =>
            val remaining = idleTimeout - elapsed
            if (remaining <= Duration.Zero) {
              logger.debug(s"connection stall timeout exceeded ($elapsed)") >>
                goAwayImmediately(H2Error.ProtocolError)
            } else
              Temporal[F].timeoutTo(
                fa,
                remaining,
                logger.debug(s"stream stall timeout exceeded") >>
                  goAwayImmediately(H2Error.ProtocolError),
              )
          }
          .onCancel(state.update { st =>
            if (st.stallStart == Some(now)) st.copy(stallStart = None) else st
          })
      }

  private[this] def writeChunk(chunk: Chunk[H2Frame]): F[Unit] = {
    // readWindow includes reserved grants so a fast peer reply is accepted even
    // before socket.write completes. Idle detection uses only transmitted credit.
    def recordReadCredit(frames: Chunk[H2Frame]): F[Unit] =
      frames.traverse_ {
        case H2Frame.WindowUpdate(0, size) =>
          state.update(s => s.copy(advertisedReadWindow = s.advertisedReadWindow + size))
        case H2Frame.WindowUpdate(id, size) =>
          mapRef.get.flatMap(
            _.get(id).traverse_(
              _.state.update(s => s.copy(advertisedReadWindow = s.advertisedReadWindow + size))
            )
          )
        case _ => F.unit
      }

    val bv = chunk.foldLeft(ByteVector.empty) { case (acc, frame) =>
      acc ++ H2Frame.toByteVector(frame)
    }

    val firstGoAway = chunk.collectFirst { case g: H2Frame.GoAway => closeWithGoAway(g) }

    withStallTimeout(socket.write(Chunk.byteVector(bv))) >>
      recordReadCredit(chunk) >>
      chunk.traverse_(frame => logger.debug(s"$addrStr Write - $frame")) >>
      state.update(s => s.copy(stallStart = None)) >>
      firstGoAway.getOrElse(F.unit)
  }

  def writeLoop: Stream[F, Nothing] =
    Stream
      .fromQueueUnterminated[F, H2Frame](outgoingQueue, 16)
      .chunks
      .foreach(writeChunk)
      .concurrently(sendReadWindowUpdates)
      .handleErrorWith(ex =>
        Stream.exec(
          logger.debug(ex)("writeLoop terminated") >>
            state.update(_.copy(closed = true))
        )
      )

  def readLoop: F[Unit] = {

    def connectionTerminated: String = s"Connection $addrStr readLoop Terminated"
    val readFromSocket: F[Option[Chunk[Byte]]] = {
      val read = socket.read(localSettings.initialWindowSize.windowSize)
      readIdleTimeout match {
        case timeout: FiniteDuration =>
          // Give the peer a full interval after local backpressure is released.
          def awaitIdle: F[Unit] =
            F.untilM_(
              F.whileM_(peerAwaitingUs)(F.sleep(timeout)) >> F.sleep(timeout)
            )(
              peerAwaitingUs.map(!_)
            )

          F.race(read, awaitIdle).flatMap {
            case Left(chunk) => F.pure(chunk)
            case Right(_) =>
              logger.debug(s"$addrStr readLoop idle timeout exceeded ($timeout)") >>
                goAway(H2Error.ProtocolError).as(Option.empty[Chunk[Byte]])
          }
        case _ => read
      }
    }

    def readNextFrame(acc: ByteVector): F[Option[(H2Frame, ByteVector)]] =
      if (acc.isEmpty) {
        readFromSocket.flatMap {
          case Some(chunk) => readNextFrame(chunk.toByteVector)
          case None =>
            logger.debug(s"$connectionTerminated with empty").as(None)
        }
      } else if (
        H2Frame.RawFrame.peekDeclaredLength(acc).exists(_ > localSettings.maxFrameSize.frameSize)
      ) {
        logger.warn(
          "Received Frame Size Larger than Allowed Frame Size - Frame Size Error - Issuing GoAway"
        ) >> goAway(H2Error.FrameSizeError) >> F.pure(None)
      } else
        H2Frame.RawFrame.fromByteVector(acc) match {
          case Some((raw, leftover)) =>
            H2Frame.fromRaw(raw) match {
              case Right(frame) => F.pure(Some((frame, leftover)))
              case Left(e) =>
                logger.warn(s"$connectionTerminated invalid Raw to Frame $e") >>
                  goAway(e) >> F.pure(None)
            }
          case None =>
            readFromSocket.flatMap {
              case Some(chunk) => readNextFrame(acc ++ chunk.toByteVector)
              case None => logger.debug(s"$connectionTerminated with $acc").as(None)
            }
        }

    def processFrame(frame: H2Frame, s: H2Connection.State[F]): F[Unit] = (frame, s) match {
      // Headers and Continuation Frames are Stateful
      // Headers if not closed MUST
      case (
            c @ H2Frame.Continuation(id, true, _),
            H2Connection.State(_, _, _, _, _, Some(headers), None, _, _),
          ) =>
        if (headers.first.identifier != id) {
          logger.warn("Invalid Continuation - Protocol Error - Issuing GoAway") >>
            goAway(H2Error.ProtocolError)
        } else {
          headers.complete(c).flatMap {
            case None =>
              logger.debug("Header block exceeds maxHeaderListSize - Issuing GoAway") >>
                goAway(H2Error.EnhanceYourCalm)

            case Some(headers) =>
              state.update(s => s.copy(headersInProgress = None)) >>
                mapRef.get.map(_.get(id)).flatMap {
                  case Some(s) =>
                    s.receiveHeaders(headers)
                  case None if isClosedStream(id, s) =>
                    logger.debug(
                      s"$addrStr Received Headers for Closed Stream $id - Closing Connection"
                    ) >>
                      goAway(H2Error.ProtocolError)
                  case None =>
                    streamCreateAndHeaders.use(_ =>
                      for {
                        stream <- initiateRemoteStreamById(id)
                        _ <- createdStreams.offer(id)
                        _ <- stream.receiveHeaders(headers)
                      } yield ()
                    )
                }
          }
        }
      case (
            c @ H2Frame.Continuation(id, true, _),
            H2Connection.State(_, _, _, _, _, None, Some(pushPromise), _, _),
          ) =>
        if (pushPromise.first.promisedStreamId != id) {
          logger.warn("Invalid Continuation - Protocol Error - Issuing GoAway") >>
            goAway(H2Error.ProtocolError)
        } else {
          pushPromise.complete(c).flatMap {
            case None =>
              logger.debug(
                "PUSH_PROMISE Header block exceeds maxHeaderListSize - Issuing GoAway"
              ) >> goAway(H2Error.EnhanceYourCalm)

            case Some(pushPromise) =>
              state.update(s => s.copy(pushPromiseInProgress = None)) >>
                mapRef.get.map(_.get(id)).flatMap {
                  case Some(s) =>
                    s.receivePushPromise(pushPromise)
                  case None =>
                    streamCreateAndHeaders.use(_ =>
                      for {
                        stream <- initiateRemoteStreamById(id)
                        _ <- createdStreams.offer(id)
                        _ <- stream.receivePushPromise(pushPromise)
                      } yield ()
                    )
                }
          }
        }
      case (
            c @ H2Frame.Continuation(id, false, _),
            H2Connection.State(_, _, _, _, _, None, Some(pushPromise), _, _),
          ) =>
        if (pushPromise.first.identifier != id) {
          logger.warn("Invalid Continuation - Protocol Error - Issuing GoAway") >>
            goAway(H2Error.ProtocolError)
        } else
          pushPromise.addContinuation(c) match {
            case None =>
              logger.debug("Header block exceeds maxHeaderListSize - Issuing GoAway") >>
                goAway(H2Error.EnhanceYourCalm)

            case Some(updated) =>
              state.update(s => s.copy(pushPromiseInProgress = updated.some))
          }

      case (
            c @ H2Frame.Continuation(id, false, _),
            H2Connection.State(_, _, _, _, _, Some(headers), None, _, _),
          ) =>
        if (headers.first.identifier != id) {
          logger.warn("Invalid Continuation - Protocol Error - Issuing GoAway") >>
            goAway(H2Error.ProtocolError)
        } else
          headers.addContinuation(c) match {
            case None =>
              logger.debug("Header block exceeds maxHeaderListSize - Issuing GoAway") >>
                goAway(H2Error.EnhanceYourCalm)
            case Some(updated) =>
              state.update(s => s.copy(headersInProgress = updated.some))
          }
      case (f, H2Connection.State(_, _, _, _, _, Some(_), None, _, _)) =>
        // Only Continuation Frames Are Valid While there is a value
        logger.warn(
          s"Continuation for headers in process, retrieved unexpected frame $f -  Protocol Error - Issuing GoAway"
        ) >>
          goAway(H2Error.ProtocolError)
      case (f, H2Connection.State(_, _, _, _, _, None, Some(_), _, _)) =>
        // Only Continuation Frames Are Valid While there is a value
        logger.warn(
          s"Continuation for push promise in process, retrieved unexpected frame $f -  Protocol Error - Issuing GoAway"
        ) >>
          goAway(H2Error.ProtocolError)

      case (h @ H2Frame.Headers(i, sd, _, true, _, _), s) =>
        if (sd.exists(s => s.dependency == i)) {
          goAway(H2Error.ProtocolError)
        } else {
          mapRef.get.map(_.get(i)).flatMap {
            case Some(s) =>
              s.receiveHeaders(h)
            case None if isClosedStream(i, s) =>
              logger.debug(
                s"$addrStr Received Headers for Closed Stream $i - Closing Connection"
              ) >>
                goAway(H2Error.ProtocolError)
            case None =>
              val isValidToCreate = connectionType match {
                case H2Connection.ConnectionType.Server => i % 2 != 0
                case H2Connection.ConnectionType.Client => i % 2 == 0
              }
              if (!isValidToCreate) {
                logger.warn(
                  s"Not Valid Stream to Create $i - $isValidToCreate, ${s.highestStream} - Protocol Error - Issuing GoAway"
                ) >>
                  goAway(H2Error.ProtocolError)
              } else {
                streamCreateAndHeaders.use(_ =>
                  for {
                    stream <- initiateRemoteStreamById(i)
                    _ <- createdStreams.offer(i)
                    _ <- stream.receiveHeaders(h)

                  } yield ()
                )
              }
          }
        }
      case (h @ H2Frame.Headers(i, sd, _, false, headerBlock, _), _) =>
        if (sd.exists(s => s.dependency == i)) goAway(H2Error.ProtocolError)
        else if (headerBlock.size > maxHeaderBlockSize)
          logger.debug("Header block exceeds maxHeaderListSize - Issuing GoAway") >>
            goAway(H2Error.EnhanceYourCalm)
        else {
          ContinuationProgress
            .start[F, H2Frame.Headers](
              h,
              (h, c) => h.copy(headerBlock = h.headerBlock ++ c),
              headerBlock.size,
              receiveHeadersTimeout,
              maxHeaderBlockSize,
              goAway(H2Error.EnhanceYourCalm),
            )
            .flatMap(headers => state.update(s => s.copy(headersInProgress = Some(headers))))
        }
      case (h @ H2Frame.PushPromise(_, true, i, _, _), s) =>
        if (connectionType == H2Connection.ConnectionType.Server) {
          logger.warn(
            "Encountered Push Promise Frame a a Server - Protocol Error - Issuing GoAway"
          ) >>
            goAway(H2Error.ProtocolError)
        } else {
          mapRef.get.map(_.get(i)).flatMap {
            case Some(s) =>
              s.receivePushPromise(h)
            case None =>
              val isValidToCreate = i % 2 == 0
              if (!isValidToCreate || i <= s.remoteHighestStream) {
                logger.warn(
                  s"Not Valid Stream to Create $i - $isValidToCreate, ${s.remoteHighestStream} - Protocol Error - Issuing GoAway"
                )
                goAway(H2Error.ProtocolError)
              } else {
                streamCreateAndHeaders.use(_ =>
                  for {
                    stream <- initiateRemoteStreamById(i)
                    _ <- createdStreams.offer(i)
                    _ <- stream.receivePushPromise(h)
                  } yield ()
                )
              }
          }
        }
      case (h @ H2Frame.PushPromise(_, false, _, headerBlock, _), _) =>
        if (headerBlock.size > maxHeaderBlockSize)
          logger.debug("Header block exceeds maxHeaderListSize - Issuing GoAway") >>
            goAway(H2Error.EnhanceYourCalm)
        else {
          ContinuationProgress
            .start[F, H2Frame.PushPromise](
              h,
              (h, c) => h.copy(headerBlock = h.headerBlock ++ c),
              headerBlock.size,
              receiveHeadersTimeout,
              maxHeaderBlockSize,
              goAway(H2Error.EnhanceYourCalm),
            )
            .flatMap(pushPromise =>
              state.update(s => s.copy(pushPromiseInProgress = Some(pushPromise)))
            )
        }

      case (H2Frame.Continuation(_, _, _), _) =>
        goAway(H2Error.ProtocolError)

      case (settings @ H2Frame.Settings(0, false, _), _) =>
        for {
          t <- state.modify { s =>
            val newSettings = H2Frame.Settings.updateSettings(settings, s.remoteSettings)
            val differenceInWindow =
              newSettings.initialWindowSize.windowSize - s.remoteSettings.initialWindowSize.windowSize
            (
              s.copy(remoteSettings = newSettings),
              (newSettings, differenceInWindow),
            )
          }
          (settings, differenceInWindow) = t

          // By the spec, this *should* always be valid
          valid <- writeWindow.change(differenceInWindow)
          _ <- goAway(H2Error.FlowControlError).unlessA(valid)

          _ <- mapRef.get.flatMap { map =>
            map.toList.traverse { case (_, stream) =>
              stream.modifyWriteWindow(differenceInWindow)
            }
          }
          _ <- offerFrame(H2Frame.Settings.Ack)
          _ <- settingsAck.complete(Either.right(settings)).void

        } yield ()
      case (H2Frame.Settings(0, true, _), _) => Applicative[F].unit
      case (H2Frame.Settings(_, _, _), _) =>
        logger.warn("Received Settings Not Oriented at Identifier 0 - Issuing goAway") >>
          goAway(H2Error.ProtocolError)
      case (g @ H2Frame.GoAway(0, _, _, _), _) =>
        mapRef.get.flatMap { m =>
          m.values.toList.traverse_(connection => connection.receiveGoAway(g))
        } >> offerFrame(H2Frame.Ping.ack)
      case (_: H2Frame.GoAway, _) =>
        goAway(H2Error.ProtocolError)
      case (H2Frame.Ping(0, false, bv), _) =>
        offerFrame(H2Frame.Ping.ack.copy(data = bv))
      case (H2Frame.Ping(0, true, _), _) => Applicative[F].unit
      case (H2Frame.Ping(_, _, _), _) =>
        goAway(H2Error.ProtocolError)

      case (H2Frame.WindowUpdate(_, 0), _) =>
        logger.warn("Encountered 0 Sized Window Update - Protocol Error - Issuing GoAway") >>
          goAway(H2Error.ProtocolError)
      case (w @ H2Frame.WindowUpdate(i, size), st) =>
        i match {
          case 0 =>
            for {
              valid <- writeWindow.change(size)
              _ <- goAway(H2Error.FlowControlError).unlessA(valid)
            } yield ()
          case otherwise =>
            mapRef.get.map(_.get(otherwise)).flatMap {
              case Some(s) =>
                s.receiveWindowUpdate(w)
              case None if isClosedStream(i, st) =>
                logger.debug(s"$addrStr Received WindowUpdate for Closed Stream $i - Ignoring")
              case None =>
                logger.warn(s"Received WindowUpdate for Idle Stream - $w, $i") >>
                  goAway(H2Error.ProtocolError)
            }
        }

      case (d @ H2Frame.Data(i, _, _, _), _) =>
        val size = d.flowControlSize
        val reserveWindow = state.modify { s =>
          val remaining = s.readWindow - size
          (
            s.copy(
              readWindow = remaining,
              advertisedReadWindow = s.advertisedReadWindow - size,
            ),
            remaining >= 0,
          )
        }
        reserveWindow.flatMap {
          case false =>
            logger.warn(
              s"Received Data Frame exceeding the connection window - Flow Control Error - Issuing GoAway"
            ) >> goAway(H2Error.FlowControlError)
          case true =>
            mapRef.get.map(_.get(i)).flatMap {
              case Some(s) => s.receiveData(d)
              case None =>
                logger.warn(
                  s"Received Data Frame for Idle Stream $i - Protocol Error - Issuing GoAway"
                ) >>
                  goAway(H2Error.ProtocolError)
            }
        }

      case (rst @ H2Frame.RstStream(i, _), st) =>
        mapRef.get.map(_.get(i)).flatMap {
          case Some(s) =>
            s.receiveRstStream(rst)
          case None if isClosedStream(i, st) =>
            logger.debug(s"$addrStr Received RstStream for Closed Stream $i - Ignoring")
          case None =>
            logger.warn(
              s"Received RstStream for Idle Stream $i - Protocol Error - Issuing GoAway"
            ) >>
              goAway(H2Error.ProtocolError)
        }
      case (H2Frame.Priority(i, _, i2, _), _) =>
        if (i == i2) goAway(H2Error.ProtocolError) // Can't depend on yourself
        else Applicative[F].unit // We Do Nothing with these presently
      case (H2Frame.Unknown(_), _) => Applicative[F].unit // Ignore Unknown Frames
    }

    def readLoopAux(acc: ByteVector): F[Unit] =
      readNextFrame(acc).flatMap {
        case Some((frame, nacc)) =>
          logger.debug(s"$addrStr Read - $frame") >>
            state.get.flatMap(processFrame(frame, _)) >>
            readLoopAux(nacc)
        case None => F.unit
      }

    readLoopAux(acc)
      .recoverWith { case H2Connection.KillWithoutMessage() =>
        logger.debug(s"ReadLoop has received that is should kill")
      }
      .guaranteeCase {
        case Outcome.Errored(e) =>
          logger.error(e)(s"ReadLoop has errored") >>
            goAway(H2Error.InternalError).attempt.void >>
            state.update(s => s.copy(closed = true))
        case _ =>
          state.update(s => s.copy(closed = true))
      }
  }

}

private[h2] object H2Connection {
  final case class State[F[_]](
      remoteSettings: H2Frame.Settings.ConnectionSettings,
      readWindow: Int,
      highestStream: Int,
      remoteHighestStream: Int,
      closed: Boolean,
      headersInProgress: Option[ContinuationProgress[F, H2Frame.Headers]],
      pushPromiseInProgress: Option[ContinuationProgress[F, H2Frame.PushPromise]],
      stallStart: Option[FiniteDuration],
      advertisedReadWindow: Long,
  )

  /** Helper class to buffer continuations to a header or push promise.
    */
  final class ContinuationProgress[F[_]: Applicative, A](
      val first: A,
      add: (A, ByteVector) => A,
      val size: Long,
      timeout: Fiber[F, Throwable, Unit],
      maxHeaderBlockSize: Long,
  ) {

    /** Buffer data from an additional frame in the continuation. If the continuation doesn't exceed
      * the size limit, the updated ContinuationProgress is returned.
      */
    def addContinuation(next: H2Frame.Continuation): Option[ContinuationProgress[F, A]] =
      if (canAcceptNextContinuation(next))
        new ContinuationProgress(
          add(first, next.headerBlockFragment),
          add,
          size + next.headerBlockFragment.size,
          timeout,
          maxHeaderBlockSize,
        ).some
      else
        None

    /** Complete the continuation buffering with a final frame. If the continuation doesn't exceed
      * the size limit, the original frame and buffered data from the continuations are returned.
      */
    def complete(last: H2Frame.Continuation): F[Option[A]] =
      timeout.cancel *> Applicative[F].pure {
        if (canAcceptNextContinuation(last))
          Some(add(first, last.headerBlockFragment))
        else
          None
      }

    private def canAcceptNextContinuation(next: H2Frame.Continuation): Boolean =
      size + next.headerBlockFragment.size <= maxHeaderBlockSize
  }

  object ContinuationProgress {
    def start[F[_]: Temporal, A](
        first: A,
        add: (A, ByteVector) => A,
        initialSize: Long,
        timeout: Duration,
        maxHeaderBlockSize: Long,
        cancel: F[Unit],
    ): F[ContinuationProgress[F, A]] =
      (Temporal[F].sleep(timeout) >> cancel.attempt.void).start
        .map(new ContinuationProgress(first, add, initialSize, _, maxHeaderBlockSize))

  }

  def initState[F[_]](
      remoteSettings: H2Frame.Settings.ConnectionSettings,
      readWindow: SettingsInitialWindowSize,
  )(implicit F: Concurrent[F]): F[Ref[F, State[F]]] =
    F.ref(
      H2Connection.State(
        remoteSettings,
        readWindow.windowSize,
        highestStream = 0,
        remoteHighestStream = 0,
        closed = false,
        headersInProgress = None,
        pushPromiseInProgress = None,
        stallStart = None,
        advertisedReadWindow = readWindow.windowSize.toLong,
      )
    )

  private[h2] class WriteWindow[F[_]] private (
      windowOpen: Semaphore[F],
      windowBytes: Ref[F, Int],
  )(implicit F: MonadCancelThrow[F]) {
    val available: F[Int] = windowBytes.get

    def take(bytes: Int): F[Int] =
      // Always allow a 0 byte request, per RFC 9113 6.9.1
      if (bytes == 0) F.pure(0)
      else {
        require(bytes > 0)

        F.bracketFull(poll => poll(windowOpen.acquire)) { _ =>
          windowBytes.flatModify { currentWindow =>
            // On the edge case where a `change` reduced the window size below
            // zero without locking, so `available` needs to be clamped
            val available = Math.max(0, Math.min(bytes, currentWindow))
            val nextWindow = currentWindow - available

            // Can't use updateOpening here as the lock was acquired even while
            // open. It needs to be released if the window is still open.
            (nextWindow, windowOpen.release.whenA(nextWindow > 0).as(available))
          }
        } {
          case (_, Outcome.Succeeded(_)) => F.unit
          case _ => windowOpen.release
        }
      }

    def change(bytes: Int): F[Boolean] =
      windowBytes.flatModify(currentWindow =>
        try {
          val nextWindow = Math.addExact(currentWindow, bytes)
          (nextWindow, updateOpening(currentWindow, nextWindow).as(true))
        } catch {
          case _: ArithmeticException => (currentWindow, F.pure(false))
        }
      )

    private def updateOpening(lastWindow: Int, nextWindow: Int): F[Unit] =
      if (nextWindow <= 0)
        // It is possible another fiber acquired the lock already, in which case
        // it will make sure the lock is updated.
        windowOpen.tryAcquire.void
      else
        windowOpen.release.whenA(lastWindow <= 0 && nextWindow > 0)
  }

  private[h2] object WriteWindow {
    def init[F[_]: Concurrent](initialSize: SettingsInitialWindowSize): F[WriteWindow[F]] =
      (
        Semaphore[F](if (initialSize.windowSize > 0) 1 else 0),
        Ref[F].of(initialSize.windowSize.toInt),
      )
        .mapN(new WriteWindow(_, _))
  }

  final case class KillWithoutMessage()
      extends RuntimeException
      with scala.util.control.NoStackTrace

  sealed trait ConnectionType
  object ConnectionType {
    case object Server extends ConnectionType
    case object Client extends ConnectionType
  }

}
