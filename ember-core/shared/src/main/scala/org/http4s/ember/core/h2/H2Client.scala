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
import fs2.io.net.unixsocket.UnixSocketAddress
import fs2.io.net.unixsocket.UnixSockets
import org.http4s.Uri.Authority
import org.http4s.Uri.Scheme
import org.http4s._
import org.http4s.ember.core.Util
import org.http4s.h2.H2Keys.Http2PriorKnowledge
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
    unix: Option[UnixSockets[F]],
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
      enableServerNameIndication: Boolean,
  ): F[H2Connection[F]] =
    connections.get.map(_.get(key).map(_._1)).flatMap {
      case Some(connection) => Applicative[F].pure(connection)
      case None =>
        createConnection(
          key,
          useTLS,
          priorKnowledge,
          enableEndpointValidation,
          enableServerNameIndication,
        ).allocated.flatMap(tup =>
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
      enableServerNameIndication: Boolean,
  ): Resource[F, H2Connection[F]] =
    createSocket(key, useTLS, priorKnowledge, enableEndpointValidation, enableServerNameIndication)
      .flatMap {
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
      enableServerNameIndication: Boolean,
  ): Resource[F, (Socket[F], SocketType)] = for {
    address <- Resource.eval(RequestKey.getAddress(key))
    baseSocket <- address match {
      case Left(address) =>
        unix
          .liftTo[Resource[F, *]](
            new RuntimeException(
              "No UnixSockets implementation available; use .withUnixSockets(...) to provide one"
            )
          )
          .flatMap(_.client(address))
      case Right(address) => sg.client(address)
    }
    socket <- {
      if (useTLS) {
        val tlsParams = Util.mkClientTLSParameters(
          address.toOption,
          enableEndpointValidation,
          enableServerNameIndication,
        )
        for {
          tlsSocket <- tls
            .clientBuilder(baseSocket)
            .withParameters(H2TLS.transform(tlsParams))
            .build
          _ <- Resource.eval(tlsSocket.write(Chunk.empty))
          socketType <- Resource.eval(parseSocketType(tlsSocket))
        } yield (tlsSocket, socketType)
      } else {
        val socketType = if (priorKnowledge) Http2 else Http1
        val out = (baseSocket, socketType)
        Resource.pure[F, (Socket[F], SocketType)](out)
      }
    }
  } yield socket

  private def parseSocketType(tlsSocket: TLSSocket[F]): F[SocketType] =
    H2TLS.protocol(tlsSocket).flatMap {
      case Some("h2") => F.pure(Http2)
      case Some("http/1.1") => F.pure(Http1)
      case Some(_) => F.raiseError(new ProtocolException("Unknown protocol"))
      case None => F.pure(Http1)
    }

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
        stateRef <- H2Connection.initState[F](
          defaultSettings,
          defaultSettings.initialWindowSize,
          localSettings.initialWindowSize,
        )
        queue <- cats.effect.std.Queue.unbounded[F, Chunk[H2Frame]] // TODO revisit
        hpack <- Hpack.create[F]
        settingsAck <- Deferred[F, Either[Throwable, H2Frame.Settings.ConnectionSettings]]
        streamCreationLock <- cats.effect.std.Semaphore[F](1)
        // data <- Resource.eval(cats.effect.std.Queue.unbounded[F, Frame.Data])
        created <- cats.effect.std.Queue.unbounded[F, Int]
        closed <- cats.effect.std.Queue.unbounded[F, Int]
      } yield new H2Connection(
        socketAdd,
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
      def processStream(i: Int): F[Unit] =
        (for {
          stream <- h2.mapRef.get.flatMap { streamMap =>
            streamMap.get(i).liftTo(new ProtocolException("Stream missing for push promise"))
          } // FOLD
          // _ <- Sync[F].delay(println(s"Push promise stream acquired for $i"))
          req <- stream.getRequest
          resp = stream.getResponse.map(_.covary[F].withBodyStream(stream.readBody))
          // _ <- Sync[F].delay(println(s"Push promise request acquired for $i"))
          outE <- onPushPromise(req, resp).flatMap {
            case Outcome.Canceled() => stream.rstStream(H2Error.RefusedStream)
            case Outcome.Errored(_) => stream.rstStream(H2Error.RefusedStream)
            case Outcome.Succeeded(f) => f
          }.attempt
          _ <- h2.mapRef.update(_ - i)
          out <- outE.liftTo[F]
        } yield out)
          .onError { case e => logger.warn(e)(s"Error Handling Push Promise") }
          .attempt
          .void

      Stream
        .fromQueueUnterminated(h2.createdStreams)
        .parEvalMap(10)(i => if (i % 2 == 0) processStream(i) else F.unit)
        .compile
        .drain
        .onError { case e => logger.info(e)(s"Server Connection Processing Halted") } // Idle etc.
    }

    def processSettings(h2: H2Connection[F]): F[Unit] = {
      val localSetts = H2Frame.Settings.ConnectionSettings.toSettings(localSettings)
      h2.outgoing.offer(Chunk.singleton(localSetts))
    }

    for {
      h2 <- Resource.eval(createH2Connection)
      _ <- h2.readLoop.background
      _ <- h2.writeLoop.compile.drain.background
      _ <- clearClosed(h2).background
      _ <- pullCreatedStreams(h2).background
      _ <- Resource.eval(processSettings(h2))
    } yield h2
  }

  def runHttp2Only(
      req: Request[F],
      enableEndpointValidation: Boolean,
      enableServerNameIndication: Boolean,
  ): Resource[F, Response[F]] = {
    // Host And Port are required
    val key = H2Client.RequestKey.fromRequest(req)
    val priorKnowledge = req.attributes.contains(Http2PriorKnowledge)
    val useTLS = req.uri.scheme match {
      case Some(Scheme.http) => false
      case Some(Scheme.https) => true
      // How Do we Choose when to use TLS, for http/1.1 this is simple its with
      // this, but with http2, there can be arbitrary schemes
      // but also probably wrong if doing websockets over http/1.1
      case Some(_) => true
      case None => !priorKnowledge
    }
    for {
      connection <- Resource.eval(
        getOrCreate(
          key,
          useTLS,
          priorKnowledge,
          enableEndpointValidation,
          enableServerNameIndication,
        )
      )
      // Stream Order Must Be Correct, so we must grab the global lock
      stream <- Resource.make(
        connection.streamCreateAndHeaders.use(_ =>
          connection.initiateLocalStream.flatMap(stream =>
            stream.sendHeaders(PseudoHeaders.requestToHeaders(req), endStream = false).as(stream)
          )
        )
      )(stream => connection.mapRef.update(m => m - stream.id))
      _ <- (stream.sendMessageBody(req) >> stream.sendTrailerHeaders(req)).background
      resp <- Resource.eval(stream.getResponse).map(_.covary[F].withBodyStream(stream.readBody))
    } yield resp
  }
}

private[ember] object H2Client {
  private type TinyClient[F[_]] = Request[F] => Resource[F, Response[F]]
  def impl[F[_]: Async: Network](
      onPushPromise: (
          org.http4s.Request[fs2.Pure],
          F[org.http4s.Response[F]],
      ) => F[Outcome[F, Throwable, Unit]],
      tlsContext: TLSContext[F],
      unixSockets: Option[UnixSockets[F]],
      logger: Logger[F],
      settings: H2Frame.Settings.ConnectionSettings = defaultSettings,
      enableEndpointValidation: Boolean,
      enableServerNameIndication: Boolean,
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
      h2 = new H2Client(Network[F], unixSockets, settings, tlsContext, mapH2, onPushPromise, logger)
    } yield (http1Client: TinyClient[F]) => { (req: Request[F]) =>
      val key = H2Client.RequestKey.fromRequest(req)
      val priorKnowledge = req.attributes.contains(Http2PriorKnowledge)
      val socketTypeF = if (priorKnowledge) Some(Http2).pure[F] else socketMap.get.map(_.get(key))
      Resource.eval(socketTypeF).flatMap {
        case Some(Http2) =>
          h2.runHttp2Only(req, enableEndpointValidation, enableServerNameIndication)
        case Some(Http1) => http1Client(req)
        case _ =>
          (
            h2.runHttp2Only(req, enableEndpointValidation, enableServerNameIndication) <*
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
  final case class RequestKey(
      scheme: Scheme,
      authorityOrAddress: Either[UnixSocketAddress, Authority],
  ) {
    override def toString: String =
      s"${scheme.value}://${authorityOrAddress.fold(_.toString, _.toString)}"
  }

  object RequestKey {
    def fromRequest[F[_]](request: Request[F]): RequestKey = {
      val uri = request.uri
      val authOrAddr = request.attributes
        .lookup(Request.Keys.UnixSocketAddress)
        .toLeft(uri.authority.getOrElse(Authority()))
      RequestKey(uri.scheme.getOrElse(Scheme.http), authOrAddr)
    }

    def getAddress[F[_]](
        requestKey: RequestKey
    )(implicit F: MonadThrow[F]): F[Either[UnixSocketAddress, SocketAddress[Host]]] =
      requestKey match {
        case RequestKey(s, Right(auth)) =>
          val port = auth.port.getOrElse(if (s == Uri.Scheme.https) 443 else 80)
          val host = auth.host.value
          for {
            host <- Host.fromString(host).liftTo[F](MissingHost())
            port <- Port.fromInt(port).liftTo[F](MissingPort())
          } yield Right(SocketAddress[Host](host, port))
        case RequestKey(_, Left(unixAddress)) => F.pure(Left(unixAddress))
      }
  }

  private[h2] case class InvalidSocketType()
      extends RuntimeException("createConnection only supports http2, and this is not available")

  private case class MissingHost() extends RuntimeException("Hostname missing")

  private case class MissingPort() extends RuntimeException("Port missing")
}
