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
import cats.effect.syntax.all._
import cats.syntax.all._
import com.comcast.ip4s._
import fs2._
import fs2.io.net._
import fs2.io.net.tls._
import org.http4s.Uri.Authority
import org.http4s.Uri.Scheme
import org.http4s._
import org.http4s.ember.core.Util
import org.typelevel.log4cats.Logger
import scodec.bits._

import scala.concurrent.duration._

import H2Frame.Settings.ConnectionSettings.{default => defaultSettings}

/*
Client
Send Preface
Send Settings
Receive Settings
Send Settings Ack
Connection
  Ping - Server Send Back Ping with Ack
  Settings - Server Send Back Settings with Ack
  GoAway - Connection Level Error - Attempt to send A GoAway back and disconnect noting number for replayable streams

  WindowUpdate -  Connection Level addressed to 0, each Data Frame send needs to decrease this window by the size
                  not including the Frame header, only the content. the number of octets that the
                  sender can transmit in addition to the existing flow-control window
  n* Streams
    Send WindowUpdate
    Receive WindowUpdate
    StreamState

    Data
    Headers
    Continuation

    Priority -- Has to outline something with connection
    RstStream - Stream Level Error

    WindowUpdate
    PushPromise - Special Case only after Open or Half-closed(remote)

 */
private[ember] class H2Client[F[_]](
    sg: SocketGroup[F],
    localSettings: H2Frame.Settings.ConnectionSettings,
    tls: TLSContext[F],
    connections: Ref[
      F,
      Map[H2Client.RequestKey, (H2Connection[F], F[Unit])],
    ],
    onPushPromise: (
        org.http4s.Request[fs2.Pure],
        F[org.http4s.Response[F]],
    ) => F[Outcome[F, Throwable, Unit]],
    logger: Logger[F],
)(implicit F: Async[F]) {
  import org.http4s._
  import H2Client._

  def getOrCreate(
      key: RequestKey,
      useTLS: Boolean,
      priorKnowledge: Boolean,
      enableEndpointValidation: Boolean,
  ): F[H2Connection[F]] =
    connections.get.map(_.get(key).map(_._1)).flatMap {
      case Some(connection) => Applicative[F].pure(connection)
      case None =>
        createConnection(key, useTLS, priorKnowledge, enableEndpointValidation).allocated.flatMap(
          tup =>
            connections
              .modify { map =>
                val current = map.get(key)
                val newMap = current.fold(map.+((key, tup)))(_ => map)
                val out = current.fold(
                  Either.left[H2Connection[F], (H2Connection[F], F[Unit])](tup._1)
                )(r => Either.right((r._1, tup._2)))
                (newMap, out)
              }
              .flatMap {
                case Right((connection, shutdown)) =>
                  shutdown.map(_ => connection)
                case Left(connection) =>
                  connection.pure[F]
              }
        )
    }

  //
  def createConnection(
      key: H2Client.RequestKey,
      useTLS: Boolean,
      priorKnowledge: Boolean,
      enableEndpointValidation: Boolean,
  ): Resource[F, H2Connection[F]] =
    createSocket(key, useTLS, priorKnowledge, enableEndpointValidation).flatMap {
      case (socket, Http2) => fromSocket(ByteVector.empty, socket, key)
      case (_, Http1) => Resource.eval(InvalidSocketType().raiseError)
    }

  // This is currently how we create http2 only sockets, will need to actually handle which
  // protocol to take
  def createSocket(
      key: RequestKey,
      useTLS: Boolean,
      priorKnowledge: Boolean,
      enableEndpointValidation: Boolean,
  ): Resource[F, (Socket[F], SocketType)] = for {
    address <- Resource.eval(RequestKey.getAddress(key))
    baseSocket <- sg.client(address)
    socket <- {
      if (useTLS) {
        for {
          tlsSocket <- tls
            .clientBuilder(baseSocket)
            .withParameters(
              H2TLS.transform(Util.mkClientTLSParameters(address.some, enableEndpointValidation))
            )
            .build
          _ <- Resource.eval(tlsSocket.write(Chunk.empty))
          protocol <- Resource.eval(H2TLS.protocol(tlsSocket))
          socketType <- protocol match {
            case Some("h2") => Resource.pure[F, SocketType](Http2)
            case Some("http/1.1") => Resource.pure[F, SocketType](Http1)
            case Some(_) =>
              Resource.raiseError[F, SocketType, Throwable](
                new ProtocolException("Unknown protocol")
              )
            case None => Resource.pure[F, SocketType](Http1)
          }
        } yield (tlsSocket, socketType)
      } else {
        val socketType = if (priorKnowledge) Http2 else Http1
        val out = (baseSocket, socketType)
        Resource.pure[F, (Socket[F], SocketType)](out)
      }
    }
  } yield socket

  // This Socket Becomes an Http2 Socket
  def fromSocket(
      acc: ByteVector,
      socket: Socket[F],
      key: RequestKey,
  ): Resource[F, H2Connection[F]] = {
    def createH2Connection: F[H2Connection[F]] =
      for {
        socketAdd <- RequestKey.getAddress(key)
        _ <- socket.write(Chunk.byteVector(Preface.clientBV))
        ref <- Concurrent[F].ref(Map[Int, H2Stream[F]]())
        initialWriteBlock <- Deferred[F, Either[Throwable, Unit]]
        stateRef <-
          Concurrent[F].ref(
            H2Connection.State(
              defaultSettings,
              defaultSettings.initialWindowSize.windowSize,
              initialWriteBlock,
              localSettings.initialWindowSize.windowSize,
              0,
              0,
              false,
              None,
              None,
            )
          )
        queue <- cats.effect.std.Queue.unbounded[F, Chunk[H2Frame]] // TODO revisit
        hpack <- Hpack.create[F]
        settingsAck <- Deferred[F, Either[Throwable, H2Frame.Settings.ConnectionSettings]]
        streamCreationLock <- cats.effect.std.Semaphore[F](1)
        // data <- Resource.eval(cats.effect.std.Queue.unbounded[F, Frame.Data])
        created <- cats.effect.std.Queue.unbounded[F, Int]
        closed <- cats.effect.std.Queue.unbounded[F, Int]
      } yield new H2Connection(
        socketAdd.host,
        socketAdd.port,
        H2Connection.ConnectionType.Client,
        localSettings,
        ref,
        stateRef,
        queue,
        created,
        closed,
        hpack,
        streamCreationLock.permit,
        settingsAck,
        acc,
        socket,
        logger,
      )

    def clearClosed(h2: H2Connection[F]): F[Unit] =
      Stream
        .fromQueueUnterminated(h2.closedStreams)
        .repeat
        .foreach(i => if (i % 2 != 0) h2.mapRef.update(m => m - i) else F.unit)
        .compile
        .drain

    def pullCreatedStreams(h2: H2Connection[F]): F[Unit] = {
      Stream
        .fromQueueUnterminated(h2.createdStreams)
        .parEvalMap(10) { i =>
          val f = if (i % 2 == 0) {
            val x = for {
              //
              stream <- h2.mapRef.get
              .map(_.get(i))
              .flatMap(
                _.liftTo(new ProtocolException("Stream missing for push promise"))
              ) // FOLD
                // _ <- Sync[F].delay(println(s"Push promise stream acquired for $i"))
              req <- stream.getRequest

              resp = stream.getResponse.map(
                _.covary[F].withBodyStream(stream.readBody)
              )
              // _ <- Sync[F].delay(println(s"Push promise request acquired for $i"))
              outE <- onPushPromise(req, resp).flatMap {
                case Outcome.Canceled() => stream.rstStream(H2Error.RefusedStream)
                case Outcome.Errored(_) => stream.rstStream(H2Error.RefusedStream)
                case Outcome.Succeeded(f) => f
              }.attempt
              _ <- h2.mapRef.update(_ - i)
              out <- outE.liftTo[F]
            } yield out
            x.onError { case e => logger.warn(e)(s"Error Handling Push Promise") }.attempt.void
          } else Applicative[F].unit
          f
        }
        .compile
        .drain
        .onError { case e => logger.info(e)(s"Server Connection Processing Halted") } // Idle etc.
    }

    for {
      h2 <- Resource.eval(createH2Connection)
      _ <- h2.readLoop.background
      _ <- h2.writeLoop.compile.drain.background
      _ <- clearClosed(h2).background
      _ <- pullCreatedStreams(h2).background

      _ <- Resource.eval(
        h2.outgoing.offer(
          Chunk.singleton(H2Frame.Settings.ConnectionSettings.toSettings(localSettings))
        )
      )
    } yield h2
  }

  def runHttp2Only(req: Request[F], enableEndpointValidation: Boolean): Resource[F, Response[F]] = {
    // Host And Port are required
    val key = H2Client.RequestKey.fromRequest(req)
    val useTLS = req.uri.scheme.map(_.value) match {
      case Some("http") => false
      case Some("https") => true
      // How Do we Choose when to use TLS, for http/1.1 this is simple its with
      // this, but with http2, there can be arbitrary schemes
      // but also probably wrong if doing websockets over http/1.1
      case Some(_) => true
      case None => true
    }
    val priorKnowledge = req.attributes.lookup(H2Keys.Http2PriorKnowledge).isDefined
    val trailers = req.attributes.lookup(Message.Keys.TrailerHeaders[F])
    for {
      connection <- Resource.eval(
        getOrCreate(key, useTLS, priorKnowledge, enableEndpointValidation)
      )
      // Stream Order Must Be Correct, so we must grab the global lock
      stream <- Resource.make(
        connection.streamCreateAndHeaders.use(_ =>
          connection.initiateLocalStream.flatMap(stream =>
            stream.sendHeaders(PseudoHeaders.requestToHeaders(req), false).as(stream)
          )
        )
      )(stream => connection.mapRef.update(m => m - stream.id))
      _ <- (req.body.chunks.noneTerminate.zipWithNext
        .evalMap {
          case (Some(c), Some(Some(_))) => stream.sendData(c.toByteVector, false)
          case (Some(c), Some(None) | None) =>
            if (trailers.isDefined) stream.sendData(c.toByteVector, false)
            else stream.sendData(c.toByteVector, true)
          case (None, _) =>
            if (trailers.isDefined) Applicative[F].unit
            else stream.sendData(ByteVector.empty, true)
        }
        .compile
        .drain >> trailers.sequence.flatMap(optTrailers =>
        optTrailers
          .flatMap(h => h.headers.map(a => (a.name.toString.toLowerCase(), a.value, false)).toNel)
          .traverse(nel => stream.sendHeaders(nel, true))
      )).background
      resp <- Resource.eval(stream.getResponse).map(_.covary[F].withBodyStream(stream.readBody))
    } yield resp
  }
}

private[ember] object H2Client {
  private type TinyClient[F[_]] = Request[F] => Resource[F, Response[F]]
  def impl[F[_]: Async](
      onPushPromise: (
          org.http4s.Request[fs2.Pure],
          F[org.http4s.Response[F]],
      ) => F[Outcome[F, Throwable, Unit]],
      tlsContext: TLSContext[F],
      logger: Logger[F],
      settings: H2Frame.Settings.ConnectionSettings = defaultSettings,
      enableEndpointValidation: Boolean,
  ): Resource[F, TinyClient[F] => TinyClient[F]] =
    for {

      mapH2 <- Resource.make {
        Concurrent[F].ref(
          Map[H2Client.RequestKey, (H2Connection[F], F[Unit])]()
        )
      } { ref =>
        ref.get.flatMap(_.toList.traverse_ { case (_, (_, s)) => s }.attempt.void)
      }
      socketMap <- Resource.eval(
        Concurrent[F].ref(Map[H2Client.RequestKey, SocketType]())
      )

      _ <- Stream
        .awakeDelay(1.seconds)
        .evalMap(_ => mapH2.get)
        .flatMap(m => Stream.emits(m.toList))
        .evalMap { case (t, (connection, shutdown)) =>
          connection.state.get.flatMap { s =>
            if (s.closed) mapH2.update(m => m - t) >> shutdown else Applicative[F].unit
          }.attempt
        }
        .compile
        .drain
        .background
      h2 = new H2Client(Network[F], settings, tlsContext, mapH2, onPushPromise, logger)
    } yield (http1Client: TinyClient[F]) => { (req: Request[F]) =>
      val key = H2Client.RequestKey.fromRequest(req)
      val priorKnowledge = req.attributes.lookup(H2Keys.Http2PriorKnowledge).isDefined
      val socketTypeF = if (priorKnowledge) Some(Http2).pure[F] else socketMap.get.map(_.get(key))
      Resource.eval(socketTypeF).flatMap {
        case Some(Http2) => h2.runHttp2Only(req, enableEndpointValidation)
        case Some(Http1) => http1Client(req)
        case None =>
          (
            h2.runHttp2Only(req, enableEndpointValidation) <*
              Resource.eval(socketMap.update(s => s + (key -> Http2)))
          ).handleErrorWith[org.http4s.Response[F], Throwable] {
            case InvalidSocketType() | MissingHost() | MissingPort() =>
              Resource.eval(socketMap.update(s => s + (key -> Http1))) >>
                http1Client(req)
            case e => Resource.raiseError[F, Response[F], Throwable](e)
          }
      }
    }

  sealed trait SocketType extends Product with Serializable
  case object Http2 extends SocketType
  case object Http1 extends SocketType

  /** Represents a key for requests that can conceivably share a [[Connection]]. */
  final case class RequestKey(scheme: Scheme, authority: Authority) {
    override def toString: String = s"${scheme.value}://${authority}"
  }

  object RequestKey {
    def fromRequest[F[_]](request: Request[F]): RequestKey = {
      val uri = request.uri
      RequestKey(uri.scheme.getOrElse(Scheme.http), uri.authority.getOrElse(Authority()))
    }

    def getAddress[F[_]: Sync](requestKey: RequestKey): F[SocketAddress[Host]] =
      requestKey match {
        case RequestKey(s, auth) =>
          val port = auth.port.getOrElse(if (s == Uri.Scheme.https) 443 else 80)
          val host = auth.host.value
          for {
            host <- Host.fromString(host).liftTo[F](MissingHost())
            port <- Port.fromInt(port).liftTo[F](MissingPort())
          } yield SocketAddress[Host](host, port)
      }
  }

  private[h2] case class InvalidSocketType()
      extends RuntimeException("createConnection only supports http2, and this is not available")

  private case class MissingHost() extends RuntimeException("Hostname missing")

  private case class MissingPort() extends RuntimeException("Port missing")
}
