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
    val outgoing: cats.effect.std.Queue[F, Chunk[H2Frame]],
    // val outgoingData: cats.effect.std.Queue[F, Frame.Data], // TODO split data rather than backpressuring frames totally

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
      outgoing,
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
      outgoing,
      closedStreams.offer(id),
      goAway,
      creditReadWindow,
      logger,
    )
    _ <- mapRef.update(m => m + (id -> stream))
    _ <- state.update(s => s.copy(remoteHighestStream = Math.max(s.remoteHighestStream, id)))
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
        outgoing.offer(Chunk.singleton(H2Frame.WindowUpdate(0, pending))))
        .whenA(pending >= localSettings.initialWindowSize.windowSize / 2) >>
        mapRef.get.flatMap(_.values.toList.traverse_(_.sendReadWindowUpdate))
    }

  def goAway(error: H2Error): F[Unit] =
    state.get.map(_.remoteHighestStream).flatMap { i =>
      val g = error.toGoAway(i)
      outgoing.offer(Chunk.singleton(g))
    } >>
      H2Connection.KillWithoutMessage().raiseError

  private[this] def writeChunk(chunk: Chunk[H2Frame]): F[Unit] = {
    def withStallTimeout[A](fa: F[A]): F[A] =
      Temporal[F].monotonic
        .flatMap { now =>
          state.modify { st =>
            val start = st.stallStart.getOrElse(now)
            (st.copy(stallStart = Some(start)), now - start)
          }
        }
        .flatMap { elapsed =>
          val remaining = idleTimeout - elapsed
          if (remaining <= Duration.Zero)
            logger.debug(s"connection stall timeout exceeded ($elapsed)") >>
              goAwayImmediately(H2Error.ProtocolError)
          else
            Temporal[F].timeoutTo(
              fa,
              remaining,
              logger.debug(s"stream stall timeout exceeded") >>
                goAwayImmediately(H2Error.ProtocolError),
            )
        }

    // Terminate the connection during a write stall. In this case, the `outgoing` queue isn't
    // progressing and will stay stuck waiting to send the go away message, so push it out directly.
    def goAwayImmediately[A](error: H2Error): F[A] =
      state.get.map(_.remoteHighestStream).flatMap { i =>
        // Last-ditch timeout in case TCP layer is stalled.
        Temporal[F].timeout(
          socket.write(Chunk.byteVector(H2Frame.toByteVector(error.toGoAway(i)))),
          idleTimeout,
        )
      } >> state.update(_.copy(closed = true)) >>
        H2Connection.KillWithoutMessage().raiseError

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

    def go(chunk: Chunk[H2Frame]): F[Unit] = state.get.flatMap { s =>
      val fullDataSize = chunk.foldLeft(0) {
        case (init, H2Frame.Data(_, data, _, _)) => init + data.size.toInt
        case (init, _) => init
      }
      // println(s"Next Write Block Window - data: $fullDataSize window:${s.writeWindow} $s")

      if (fullDataSize <= s.writeWindow && s.writeWindow > 0) {
        val bv = chunk.foldLeft(ByteVector.empty) { case (acc, frame) =>
          acc ++ H2Frame.toByteVector(frame)
        }
        withStallTimeout(socket.write(Chunk.byteVector(bv))) >>
          recordReadCredit(chunk) >>
          state.update(s =>
            s.copy(writeWindow = s.writeWindow - fullDataSize, stallStart = None)
          ) >>
          chunk.traverse_(frame => logger.debug(s"$addrStr Write - $frame"))
      } else {
        val (nonData, after) = chunk.indexWhere(_.isInstanceOf[H2Frame.Data]) match {
          case None => (chunk, Chunk.empty[H2Frame])
          case Some(ix) => chunk.splitAt(ix)
        }

        val bv = nonData.foldLeft(ByteVector.empty) { case (acc, frame) =>
          acc ++ H2Frame.toByteVector(frame)
        }
        withStallTimeout(socket.write(Chunk.byteVector(bv))) >>
          recordReadCredit(nonData) >>
          nonData.traverse_(frame => logger.debug(s"$addrStr Write - $frame")) >>
          { // avoid stalling if only control frames were written
            if (after.isEmpty) state.update(s => s.copy(stallStart = None))
            else withStallTimeout(s.writeBlock.get.rethrow) >> go(after)
          }
      }
    }

    val firstGoAway = chunk.collectFirst { case g: H2Frame.GoAway =>
      mapRef.get.flatMap { m =>
        m.values.toList.traverse_(connection => connection.receiveGoAway(g))
      } >> state.update(s => s.copy(closed = true))
    }

    firstGoAway.getOrElse(F.unit) >> go(chunk)
  }

  def writeLoop: Stream[F, Nothing] =
    Stream
      .fromQueueUnterminated[F, Chunk[H2Frame]](outgoing, Int.MaxValue)
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
            H2Connection.State(_, _, _, _, _, _, _, Some(headers), None, _, _),
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
            H2Connection.State(_, _, _, _, _, _, _, None, Some(pushPromise), _, _),
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
            H2Connection.State(_, _, _, _, _, _, _, None, Some(pushPromise), _, _),
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
            H2Connection.State(_, _, _, _, _, _, _, Some(headers), None, _, _),
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
      case (f, H2Connection.State(_, _, _, _, _, _, _, Some(_), None, _, _)) =>
        // Only Continuation Frames Are Valid While there is a value
        logger.warn(
          s"Continuation for headers in process, retrieved unexpected frame $f -  Protocol Error - Issuing GoAway"
        ) >>
          goAway(H2Error.ProtocolError)
      case (f, H2Connection.State(_, _, _, _, _, _, _, None, Some(_), _, _)) =>
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
          newWriteBlock <- Deferred[F, Either[Throwable, Unit]]
          t <- state.modify { s =>
            val newSettings = H2Frame.Settings.updateSettings(settings, s.remoteSettings)
            val differenceInWindow =
              newSettings.initialWindowSize.windowSize - s.remoteSettings.initialWindowSize.windowSize
            (
              s.copy(
                remoteSettings = newSettings,
                writeWindow = s.writeWindow,
                writeBlock = newWriteBlock,
              ),
              (newSettings, differenceInWindow, s.writeBlock),
            )
          }
          (settings, difference, oldWriteBlock) = t
          _ <- oldWriteBlock.complete(Either.unit)
          _ <- mapRef.get.flatMap { map =>
            map.toList.traverse { case (_, stream) =>
              stream.modifyWriteWindow(difference)
            }
          }
          _ <- outgoing.offer(Chunk.singleton(H2Frame.Settings.Ack))
          _ <- settingsAck.complete(Either.right(settings)).void

        } yield ()
      case (H2Frame.Settings(0, true, _), _) => Applicative[F].unit
      case (H2Frame.Settings(_, _, _), _) =>
        logger.warn("Received Settings Not Oriented at Identifier 0 - Issuing goAway") >>
          goAway(H2Error.ProtocolError)
      case (g @ H2Frame.GoAway(0, _, _, _), _) =>
        mapRef.get.flatMap { m =>
          m.values.toList.traverse_(connection => connection.receiveGoAway(g))
        } >> outgoing.offer(Chunk.singleton(H2Frame.Ping.ack))
      case (_: H2Frame.GoAway, _) =>
        goAway(H2Error.ProtocolError)
      case (H2Frame.Ping(0, false, bv), _) =>
        outgoing.offer(Chunk.singleton(H2Frame.Ping.ack.copy(data = bv)))
      case (H2Frame.Ping(0, true, _), _) => Applicative[F].unit
      case (H2Frame.Ping(_, _, _), _) =>
        goAway(H2Error.ProtocolError)

      case (H2Frame.WindowUpdate(_, 0), _) =>
        logger.warn("Encountered 0 Sized Window Update - Procol Error - Issuing GoAway") >>
          goAway(H2Error.ProtocolError)
      case (w @ H2Frame.WindowUpdate(i, size), st) =>
        i match {
          case 0 =>
            for {
              newWriteBlock <- Deferred[F, Either[Throwable, Unit]]
              t <- state.modify { s =>
                val newSize = s.writeWindow + size
                val sizeValid =
                  (s.writeWindow >= 0 && newSize >= 0) || s.writeWindow < 0 // Less than 2^31-1 and didn't overflow, going negative
                (
                  s.copy(writeBlock = newWriteBlock, writeWindow = s.writeWindow + size),
                  (s.writeBlock, sizeValid),
                )
              }
              (oldWriteBlock, valid) = t
              _ <- oldWriteBlock.complete(Either.unit)
              _ <- {
                if (!valid) goAway(H2Error.FlowControlError)
                else Applicative[F].unit
              }
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

      case (d @ H2Frame.Data(i, _, _, _), st) =>
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
              case None if isClosedStream(i, st) =>
                creditReadWindow(size) >> (connectionType match {
                  // We forget a stream as soon as we reset it, so its DATA may be in flight
                  case H2Connection.ConnectionType.Client =>
                    logger.debug(s"$addrStr Received Data Frame for Closed Stream $i - Ignoring")
                  // We forget a stream a second after closing it, beyond any round trip
                  case H2Connection.ConnectionType.Server =>
                    logger.debug(
                      s"$addrStr Received Data Frame for Closed Stream $i - Resetting"
                    ) >>
                      outgoing.tryOffer(Chunk.singleton(H2Error.StreamClosed.toRst(i))).void
                })
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
      writeWindow: Int,
      writeBlock: Deferred[F, Either[Throwable, Unit]],
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
      writeWindow: SettingsInitialWindowSize,
      readWindow: SettingsInitialWindowSize,
  )(implicit F: Async[F]): F[Ref[F, State[F]]] =
    for {
      writeBlock <- Deferred[F, Either[Throwable, Unit]]
      state = H2Connection.State(
        remoteSettings,
        writeWindow.windowSize,
        writeBlock,
        readWindow.windowSize,
        highestStream = 0,
        remoteHighestStream = 0,
        closed = false,
        headersInProgress = None,
        pushPromiseInProgress = None,
        stallStart = None,
        advertisedReadWindow = readWindow.windowSize.toLong,
      )
      ref <- F.ref(state)
    } yield ref

  final case class KillWithoutMessage()
      extends RuntimeException
      with scala.util.control.NoStackTrace

  sealed trait ConnectionType
  object ConnectionType {
    case object Server extends ConnectionType
    case object Client extends ConnectionType
  }

}
