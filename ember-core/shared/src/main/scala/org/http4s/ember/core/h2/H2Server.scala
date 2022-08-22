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
import cats.effect.std.Semaphore
import cats.effect.syntax.all._
import cats.syntax.all._
import fs2._
import fs2.io.IOException
import fs2.io.net._
import org.http4s._
import org.typelevel.ci._
import org.typelevel.log4cats.Logger
import scodec.bits._

import scala.concurrent.duration._

import H2Frame.Settings.ConnectionSettings.{default => defaultSettings}

private[ember] object H2Server {

  /*
  3 Mechanism into H2

  TlsContext => Yes => ALPN =>  h2       => HTTP2
                                http/1.1 => HTTP1
                                Nothing  => Http1
                No  =>                      Http1

  This is the tricky one, must read the initial bytes
  if they are the prelude then upgrade, but only on when
  the connection is first established

  Client Prelude Bytes                   => Http2 // Http2-prior-kno
  checkConnectionPreface
  Note: implementations that support HTTP/2 over TLS MUST use protocol
    negotiation in TLS.
    So if TLS is used then this method is not allowed.


  H2c
            Request
            Connection: Upgrade, HTTP2-Settings
            Upgrade: h2c
            HTTP2-Settings: <base64url encoding of HTTP/2 SETTINGS payload>
              =>
                HTTP/1.1 101 Switching Protocols
                Connection: Upgrade
                Upgrade: h2c

                Socket                    => Http2

            Normal                        => Resp

   */

  private val upgradeResponse: Response[fs2.Pure] = Response(
    status = Status.SwitchingProtocols,
    httpVersion = HttpVersion.`HTTP/1.1`,
    headers = Headers(
      "connection" -> "Upgrade",
      "upgrade" -> "h2c",
    ),
  )
  // Apply this, if it ever becomes Some, then rather than the next request, become an h2 connection
  def h2cUpgradeHttpRoute[F[_]: Concurrent]: HttpRoutes[F] =
    cats.data.Kleisli[({ type L[A] = cats.data.OptionT[F, A] })#L, Request[F], Response[F]] {
      (req: Request[F]) =>
        val connectionCheck = req.headers
          .get[org.http4s.headers.Connection]
          .exists(connection =>
            connection.values.contains_(ci"upgrade") && connection.values
              .contains_(ci"http2-settings")
          )

        // checks are cascading so we execute the least amount of work
        // if there is no upgrade, which is the likely case.
        val upgradeCheck = connectionCheck && {
          req.headers
            .get(ci"upgrade")
            .exists(upgrade => upgrade.map(r => r.value).exists(_ === "h2c"))
        }

        val settings: Option[H2Frame.Settings.ConnectionSettings] = if (upgradeCheck) {
          req.headers
            .get(ci"http2-settings")
            .collectFirstSome(settings =>
              settings.map(_.value).collectFirstSome { value =>
                for {
                  bv <- ByteVector.fromBase64(value, Bases.Alphabets.Base64Url) // Base64 Url
                  settings <- H2Frame.Settings
                    .fromPayload(bv, 0, false)
                    .toOption // This isn't an entire frame
                  // It is Just the Payload section of the frame
                } yield H2Frame.Settings
                  .updateSettings(settings, H2Frame.Settings.ConnectionSettings.default)
              }
            )
        } else None
        val upgrade = connectionCheck && upgradeCheck
        (settings, upgrade) match {
          case (Some(settings), true) =>
            val bb: F[ByteVector] = req.body.compile.to(ByteVector)
            val fres: F[Response[F]] = bb.map { bv =>
              val newReq: Request[fs2.Pure] = Request[fs2.Pure](
                req.method,
                req.uri,
                HttpVersion.`HTTP/2`,
                req.headers,
                Entity.Strict(bv),
                req.attributes,
              )
              upgradeResponse.withAttribute(H2Keys.H2cUpgrade, (settings, newReq))
            }
            cats.data.OptionT.liftF(fres)

          case (_, _) => cats.data.OptionT.none
        }
    }

  def h2cUpgradeMiddleware[F[_]: Concurrent](app: HttpApp[F]): HttpApp[F] =
    cats.data.Kleisli { (req: Request[F]) =>
      h2cUpgradeHttpRoute
        .run(req)
        .getOrElseF(app.run(req))
    }

  // Call on a new connection for http2-prior-knowledge
  // If left 1.1 if right 2
  def checkConnectionPreface[F[_]: MonadThrow](socket: Socket[F]): F[Either[ByteVector, Unit]] =
    socket.read(Preface.clientBV.size.toInt).flatMap {
      case Some(s) =>
        val received = s.toByteVector
        if (received == Preface.clientBV) Applicative[F].pure(Either.unit)
        else Applicative[F].pure(Either.left(received))
      case None =>
        new IOException("Input Closed Before Receiving Data").raiseError
    }

  // For Anything that is guaranteed to only be h2 this method will fail
  // unless the connection preface is there. For example after ALPN negotiation
  // on an SSL connection.
  def requireConnectionPreface[F[_]: MonadThrow](socket: Socket[F]): F[Unit] =
    checkConnectionPreface(socket).flatMap {
      case Left(_) => new IllegalArgumentException("Invalid Connection Preface").raiseError
      case Right(unit) => unit.pure[F]
    }

  // This is the full h2 management of a socket
  // AFTER the connection preface.
  // allowing delegation
  def fromSocket[F[_]](
      socket: Socket[F],
      httpApp: HttpApp[F],
      localSettings: H2Frame.Settings.ConnectionSettings,
      logger: Logger[F],
      // Only Used for http1 upgrade where remote settings are provided prior to escalation
      initialRemoteSettings: H2Frame.Settings.ConnectionSettings = defaultSettings,
      initialRequest: Option[Request[fs2.Pure]] = None,
  )(implicit F: Async[F]): Resource[F, Unit] = {
    import cats.effect.kernel.instances.spawn._

    // h2c Initial Request Communication on h2c Upgrade
    def sendInitialRequest(h2: H2Connection[F])(req: Request[Pure]): F[Unit] =
      for {
        h2Stream <- h2.initiateRemoteStreamById(1)
        s <- h2Stream.state.modify { s =>
          val x = s.copy(state = H2Stream.StreamState.HalfClosedRemote)
          (x, x)
        }
        _ <- s.request.complete(Either.right(req))
        er = Either.right(req.body.compile.to(fs2.Collector.supportsByteVector(ByteVector)))
        _ <- s.readBuffer.offer(er)
        _ <- s.writeBlock.complete(Either.right(()))
      } yield ()

    def holdWhileOpen(stateRef: Ref[F, H2Connection.State[F]]): F[Unit] =
      F.sleep(1.seconds) >> stateRef.get.map(_.closed).ifM(F.unit, holdWhileOpen(stateRef))

    def initH2Connection: F[(H2Connection[F], Semaphore[F])] = for {
      address <- socket.remoteAddress
      (remotehost, remoteport) = (address.host, address.port)
      ref <- Concurrent[F].ref(Map[Int, H2Stream[F]]())
      initialWriteBlock <- Deferred[F, Either[Throwable, Unit]]
      stateRef <-
        Concurrent[F].ref(
          H2Connection.State(
            initialRemoteSettings,
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
      streamCreationLock <- Semaphore[F](1)
      // data <- Resource.eval(cats.effect.std.Queue.unbounded[F, Frame.Data])
      created <- cats.effect.std.Queue.unbounded[F, Int]
      closed <- cats.effect.std.Queue.unbounded[F, Int]
      h2 = new H2Connection(
        remotehost,
        remoteport,
        H2Connection.ConnectionType.Server,
        localSettings,
        ref,
        stateRef,
        queue,
        created,
        closed,
        hpack,
        streamCreationLock.permit,
        settingsAck,
        ByteVector.empty,
        socket,
        logger,
      )
    } yield (h2, streamCreationLock)

    def clearClosedStreams(h2: H2Connection[F]): F[Unit] =
      Stream
        .fromQueueUnterminated(h2.closedStreams)
        .map(i =>
          Stream.eval(
            // Max Time After Close We Will Still Accept Messages
            (Temporal[F].sleep(1.seconds) >>
              h2.mapRef.update(m => m - i)).timeout(15.seconds).attempt.start
          )
        )
        .parJoin(localSettings.maxConcurrentStreams.maxConcurrency)
        .compile
        .drain

    def processCreatedStream(
        h2: H2Connection[F],
        streamCreationLock: Semaphore[F],
        i: Int,
    ): F[Unit] =
      for {
        stream <- h2.mapRef.get.map(_.get(i)).map(_.get) // FOLD
        req <- stream.getRequest.map(_.withBodyStream(stream.readBody))
        resp <- httpApp(req)
        _ <- stream.sendHeaders(PseudoHeaders.responseToHeaders(resp), false)
        // Push Promises
        pp = resp.attributes.lookup(H2Keys.PushPromises)
        pushEnabled <- h2.state.get.map(_.remoteSettings.enablePush.isEnabled)
        streams <- (Alternative[Option].guard(pushEnabled) >> pp).fold(
          Applicative[F].pure(List.empty[(Request[fs2.Pure], H2Stream[F])])
        ) { l =>
          l.traverse { req =>
            streamCreationLock.permit.use[(Request[Pure], H2Stream[F])](_ =>
              h2.initiateLocalStream.flatMap { stream =>
                stream
                  .sendPushPromise(i, PseudoHeaders.requestToHeaders(req))
                  .map(_ => (req, stream))
              }
            )
          }
        }
        // _ <- Console.make[F].println("Writing Streams Commpleted")
        responses <- streams.parTraverse { case (req, stream) =>
          for {
            resp <- httpApp(req)
            // _ <- Console.make[F].println("Push Promise Response Completed")
            _ <- stream.sendHeaders(
              PseudoHeaders.responseToHeaders(resp),
              false,
            ) // PP Response
          } yield (resp.body, stream)
        }

        _ <- responses.parTraverse { case (_, stream) =>
          resp.body.chunks
            .evalMap(c => stream.sendData(c.toByteVector, false))
            .compile
            .drain >> // PP Resp Body
            stream.sendData(ByteVector.empty, true)
        }
        trailers = resp.attributes.lookup(Message.Keys.TrailerHeaders[F])
        _ <- resp.body.chunks.noneTerminate.zipWithNext
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
          .drain // Initial Resp Body
        optTrailers <- trailers.sequence
        optNel = optTrailers.flatMap(h =>
          h.headers.map(a => (a.name.toString.toLowerCase(), a.value, false)).toNel
        )
        _ <- optNel.traverse(nel => stream.sendHeaders(nel, true))
      } yield ()

    def processCreatedStreams(h2: H2Connection[F], streamCreationLock: Semaphore[F]): F[Unit] =
      Stream
        .fromQueueUnterminated(h2.createdStreams)
        .map(i => Stream.eval(processCreatedStream(h2, streamCreationLock, i).attempt))
        .parJoin(localSettings.maxConcurrentStreams.maxConcurrency)
        .compile
        .drain
        .onError { case e => logger.error(e)(s"Server Connection Processing Halted") }

    for {
      pair <- Resource.eval(initH2Connection)
      h2 = pair._1
      streamCreationLock = pair._2
      _ <- h2.writeLoop.compile.drain.background
      _ <- Resource.eval(
        h2.outgoing.offer(
          Chunk.singleton(H2Frame.Settings.ConnectionSettings.toSettings(localSettings))
        )
      )
      _ <- h2.readLoop.background
      // h2c Initial Request Communication on h2c Upgrade
      _ <- Resource.eval(
        initialRequest.traverse_(req => sendInitialRequest(h2)(req) >> h2.createdStreams.offer(1))
      )
      _ <- clearClosedStreams(h2).background
      _ <- processCreatedStreams(h2, streamCreationLock).background
      _ <- Resource.eval(
        h2.state.update(s => s.copy(writeWindow = s.remoteSettings.initialWindowSize.windowSize))
      )
      _ <- Resource.eval(holdWhileOpen(h2.state))
    } yield ()
  }
}
