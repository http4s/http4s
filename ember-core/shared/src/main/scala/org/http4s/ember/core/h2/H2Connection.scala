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
import cats.syntax.all._
import com.comcast.ip4s.GenSocketAddress
import fs2._
import fs2.concurrent.Channel
import fs2.io.net.Socket
import org.typelevel.log4cats.Logger
import scodec.bits._

import H2Frame.Settings.SettingsInitialWindowSize

private[h2] class H2Connection[F[_]](
    address: GenSocketAddress,
    connectionType: H2Connection.ConnectionType,
    localSettings: H2Frame.Settings.ConnectionSettings,
    val mapRef: Ref[F, Map[Int, H2Stream[F]]],
    val state: Ref[F, H2Connection.State[F]], // odd if client, even if server
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
      )
    )
    stream = new H2Stream(
      id,
      localSettings,
      connectionType,
      state.get.map(_.remoteSettings),
      refState,
      hpack,
      outgoing,
      closedStreams.offer(id),
      goAway,
      logger,
    )
    _ <- mapRef.update(m => m + (id -> stream))
  } yield stream

  def initiateRemoteStreamById(id: Int): F[H2Stream[F]] = for {
    t <- state.get.map(s => (s.remoteSettings, s.remoteHighestStream))
    (settings, highestStream) = t
    writeBlock <- Deferred[F, Either[Throwable, Unit]]
    request <- Deferred[F, Either[Throwable, org.http4s.Request[fs2.Pure]]]
    response <- Deferred[F, Either[Throwable, org.http4s.Response[fs2.Pure]]]
    trailers <- Deferred[F, Either[Throwable, org.http4s.Headers]]
    body <- Channel.unbounded[F, Either[Throwable, ByteVector]]
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
      )
    )
    stream = new H2Stream(
      id,
      localSettings,
      connectionType,
      state.get.map(_.remoteSettings),
      refState,
      hpack,
      outgoing,
      closedStreams.offer(id),
      goAway,
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

  def goAway(error: H2Error): F[Unit] =
    state.get.map(_.remoteHighestStream).flatMap { i =>
      val g = error.toGoAway(i)
      outgoing.offer(Chunk.singleton(g))
    } >>
      H2Connection.KillWithoutMessage().raiseError

  @annotation.nowarn("cat=deprecation")
  private[this] def writeChunk(chunk: Chunk[H2Frame]): F[Unit] = {
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
        state.update(s => s.copy(writeWindow = s.writeWindow - fullDataSize)) >>
          socket.write(Chunk.byteVector(bv)) >>
          chunk.traverse_(frame => logger.debug(s"$addrStr Write - $frame"))
      } else {
        val (nonData, after) = chunk.indexWhere(_.isInstanceOf[H2Frame.Data]) match {
          case None => (chunk, Chunk.empty[H2Frame])
          case Some(ix) => chunk.splitAt(ix)
        }

        val bv = nonData.foldLeft(ByteVector.empty) { case (acc, frame) =>
          acc ++ H2Frame.toByteVector(frame)
        }
        socket.write(Chunk.byteVector(bv)) >>
          nonData.traverse_(frame => logger.debug(s"$addrStr Write - $frame")) >>
          s.writeBlock.get.rethrow >>
          go(after)
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
      .handleErrorWith(ex => Stream.exec(logger.debug(ex)("writeLoop terminated")))

  // TODO Split Frames between Data and Others Hold Data If we are at cap
  //  Currently will backpressure at the data frame till its cleared

  def readLoop: F[Unit] = {
    def connectionTerminated: String = s"Connection $addrStr readLoop Terminated"
    val readFromSocket: F[Option[Chunk[Byte]]] =
      socket.read(localSettings.initialWindowSize.windowSize)

    def readNextFrame(acc: ByteVector): F[Option[(H2Frame, ByteVector)]] =
      if (acc.isEmpty) {
        readFromSocket.flatMap {
          case Some(chunk) => readNextFrame(chunk.toByteVector)
          case None =>
            logger.debug(s"$connectionTerminated with empty").as(None)
        }
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
            H2Connection.State(_, _, _, _, _, _, _, Some((h, cs)), None),
          ) =>
        if (h.identifier == id) {
          state.update(s => s.copy(headersInProgress = None)) >>
            mapRef.get.map(_.get(id)).flatMap {
              case Some(s) =>
                s.receiveHeaders(h, cs ::: c :: Nil: _*)
              case None =>
                streamCreateAndHeaders.use(_ =>
                  for {
                    stream <- initiateRemoteStreamById(id)
                    _ <- createdStreams.offer(id)
                    _ <- stream.receiveHeaders(h, cs ::: c :: Nil: _*)
                  } yield ()
                )
            }
        } else {
          logger.warn("Invalid Continuation - Protocol Error - Issuing GoAway") >>
            goAway(H2Error.ProtocolError)
        }
      case (
            c @ H2Frame.Continuation(id, true, _),
            H2Connection.State(_, _, _, _, _, _, _, None, Some((p, cs))),
          ) =>
        if (p.promisedStreamId == id) {
          state.update(s => s.copy(headersInProgress = None)) >>
            mapRef.get.map(_.get(id)).flatMap {
              case Some(s) =>
                s.receivePushPromise(p, cs ::: c :: Nil: _*)
              case None =>
                streamCreateAndHeaders.use(_ =>
                  for {
                    stream <- initiateRemoteStreamById(id)
                    _ <- createdStreams.offer(id)
                    _ <- stream.receivePushPromise(p, cs ::: c :: Nil: _*)

                  } yield ()
                )
            }
        } else {
          logger.warn("Invalid Continuation - Protocol Error - Issuing GoAway") >>
            goAway(H2Error.ProtocolError)
        }
      case (
            c @ H2Frame.Continuation(id, false, _),
            H2Connection.State(_, _, _, _, _, _, _, None, Some((h, cs))),
          ) =>
        if (h.identifier == id) {
          state.update(s => s.copy(pushPromiseInProgress = (h, cs ::: c :: Nil).some))
        } else {
          logger.warn("Invalid Continuation - Protocol Error - Issuing GoAway") >>
            goAway(H2Error.ProtocolError)
        }

      case (
            c @ H2Frame.Continuation(id, false, _),
            H2Connection.State(_, _, _, _, _, _, _, Some((h, cs)), None),
          ) =>
        if (h.identifier == id) {
          state.update(s => s.copy(headersInProgress = (h, cs ::: c :: Nil).some))
        } else {
          logger.warn("Invalid Continuation - Protocol Error - Issuing GoAway") >>
            goAway(H2Error.ProtocolError)
        }
      case (f, H2Connection.State(_, _, _, _, _, _, _, Some(_), None)) =>
        // Only Continuation Frames Are Valid While there is a value
        logger.warn(
          s"Continuation for headers in process, retrieved unexpected frame $f -  Protocol Error - Issuing GoAway"
        ) >>
          goAway(H2Error.ProtocolError)
      case (f, H2Connection.State(_, _, _, _, _, _, _, None, Some(_))) =>
        // Only Continuation Frames Are Valid While there is a value
        logger.warn(
          s"Continuation for push promise in process, retrieved unexpected frame $f -  Protocol Error - Issuing GoAway"
        ) >>
          goAway(H2Error.ProtocolError)

      case (h @ H2Frame.Headers(i, sd, _, true, headerBlock, _), s) =>
        val size = headerBlock.size.toInt
        if (size > s.remoteSettings.maxFrameSize.frameSize) {
          logger.warn("Header Size too large for frame size - FrameSizeError - Issuing GoAway") >>
            goAway(H2Error.FrameSizeError)
        } else if (sd.exists(s => s.dependency == i)) {
          goAway(H2Error.ProtocolError)
        } else {
          mapRef.get.map(_.get(i)).flatMap {
            case Some(s) =>
              s.receiveHeaders(h)
            case None =>
              val isValidToCreate = connectionType match {
                case H2Connection.ConnectionType.Server => i % 2 != 0
                case H2Connection.ConnectionType.Client => i % 2 == 0
              }
              if (!isValidToCreate || i <= s.remoteHighestStream) {
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
      case (h @ H2Frame.Headers(i, sd, _, false, headerBlock, _), s) =>
        val size = headerBlock.size.toInt
        if (size > s.remoteSettings.maxFrameSize.frameSize) goAway(H2Error.FrameSizeError)
        else if (sd.exists(s => s.dependency == i)) goAway(H2Error.ProtocolError)
        else {
          state.update(s => s.copy(headersInProgress = Some((h, List.empty))))
        }
      case (h @ H2Frame.PushPromise(_, true, i, headerBlock, _), s) =>
        val size = headerBlock.size.toInt
        if (connectionType == H2Connection.ConnectionType.Server) {
          logger.warn(
            "Encountered Push Promise Frame a a Server - Protocol Error - Issuing GoAway"
          ) >>
            goAway(H2Error.ProtocolError)
        } else if (size > s.remoteSettings.maxFrameSize.frameSize) {
          logger.warn("Header Size too large for frame size - FrameSizeError - Issuing GoAway") >>
            goAway(H2Error.FrameSizeError)
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
      case (h @ H2Frame.PushPromise(_, false, _, headerBlock, _), s) =>
        val size = headerBlock.size.toInt
        if (size > s.remoteSettings.maxFrameSize.frameSize) goAway(H2Error.FrameSizeError)
        else {
          state.update(s => s.copy(pushPromiseInProgress = Some((h, List.empty))))
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
      case (w @ H2Frame.WindowUpdate(i, size), _) =>
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
              case None =>
                logger.warn(s"Received WindowUpdate for Closed or Idle Stream - $w, $i") >>
                  goAway(H2Error.ProtocolError)
            }
        }

      case (d @ H2Frame.Data(i, data, _, _), _) =>
        val size = data.size.toInt
        if (size > localSettings.maxFrameSize.frameSize) {
          logger.warn(
            "Receive Data Size Larger than Allowed Frame Size - Frame Size Error - Issuing GoAway"
          ) >>
            goAway(H2Error.FrameSizeError)
        } else {
          mapRef.get.map(_.get(i)).flatMap {
            case Some(s) =>
              for {
                st <- state.get
                newSize = st.readWindow - d.data.size.toInt

                needsWindowUpdate = newSize <= (localSettings.initialWindowSize.windowSize / 2)
                _ <- state.update(s =>
                  s.copy(readWindow =
                    if (needsWindowUpdate) localSettings.initialWindowSize.windowSize
                    else newSize.toInt
                  )
                )
                _ <-
                  if (needsWindowUpdate)
                    outgoing.offer(
                      Chunk.singleton(
                        H2Frame.WindowUpdate(
                          0,
                          st.remoteSettings.initialWindowSize.windowSize - newSize.toInt,
                        )
                      )
                    )
                  else Applicative[F].unit
                _ <- s.receiveData(d)
              } yield ()
            case None =>
              logger.warn(
                s"Received Data Frame for Idle or Closed Stream $i - Protocol Error - Issuing GoAway"
              ) >>
                goAway(H2Error.ProtocolError)
          }
        }

      case (rst @ H2Frame.RstStream(i, _), _) =>
        mapRef.get.map(_.get(i)).flatMap {
          case Some(s) =>
            s.receiveRstStream(rst)
          case None =>
            logger.warn(
              s"Received RstStream for Idle or Closed Stream $i - Protocol Error - Issuing GoAway"
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

    F.guaranteeCase(readLoopAux(acc)) {
      case Outcome.Errored(H2Connection.KillWithoutMessage()) =>
        logger.debug(s"ReadLoop has received that is should kill") >>
          state.update(s => s.copy(closed = true))
      case Outcome.Errored(e) =>
        logger.error(e)(s"ReadLoop has errored") >>
          goAway(H2Error.InternalError) >>
          state.update(s => s.copy(closed = true))

      case _ => state.update(s => s.copy(closed = true))
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
      headersInProgress: Option[(H2Frame.Headers, List[H2Frame.Continuation])],
      pushPromiseInProgress: Option[(H2Frame.PushPromise, List[H2Frame.Continuation])],
  )

  def initState[F[_]](
      remoteSettings: H2Frame.Settings.ConnectionSettings,
      writeWindow: SettingsInitialWindowSize,
      readWindow: SettingsInitialWindowSize,
  )(implicit F: Async[F]): F[Ref[F, State[F]]] =
    Deferred[F, Either[Throwable, Unit]].flatMap { writeBlock =>
      val state = H2Connection.State(
        remoteSettings,
        writeWindow.windowSize,
        writeBlock,
        readWindow.windowSize,
        highestStream = 0,
        remoteHighestStream = 0,
        closed = false,
        headersInProgress = None,
        pushPromiseInProgress = None,
      )
      F.ref(state)
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
