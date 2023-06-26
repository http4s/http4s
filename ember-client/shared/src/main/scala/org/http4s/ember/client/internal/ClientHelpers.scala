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

package org.http4s.ember.client.internal

import _root_.fs2.io.net.SocketGroup
import _root_.fs2.io.net.tls._
import _root_.org.http4s.ember.core.Encoder
import _root_.org.http4s.ember.core.Parser
import _root_.org.http4s.ember.core.Util._
import cats._
import cats.data.NonEmptyList
import cats.effect.kernel.Async
import cats.effect.kernel.Clock
import cats.effect.kernel.Concurrent
import cats.effect.kernel.Ref
import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import cats.effect.syntax.all._
import cats.syntax.all._
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import com.comcast.ip4s.SocketAddress
import fs2.io.ClosedChannelException
import fs2.io.net._
import org.http4s._
import org.http4s.client.RequestKey
import org.http4s.client.middleware._
import org.http4s.ember.client._
import org.http4s.ember.core.EmberException
import org.http4s.ember.core.Util
import org.http4s.headers.Connection
import org.http4s.headers.Date
import org.http4s.headers.`User-Agent`
import org.typelevel.ci._
import org.typelevel.keypool._

import java.io.IOException
import scala.concurrent.duration._

private[client] object ClientHelpers {
  def requestToSocketWithKey[F[_]: MonadThrow](
      request: Request[F],
      tlsContextOpt: Option[TLSContext[F]],
      enableEndpointValidation: Boolean,
      enableServerNameIndication: Boolean,
      sg: SocketGroup[F],
      additionalSocketOptions: List[SocketOption],
  ): Resource[F, RequestKeySocket[F]] = {
    val requestKey = RequestKey.fromRequest(request)
    requestKeyToSocketWithKey[F](
      requestKey,
      tlsContextOpt,
      enableEndpointValidation,
      enableServerNameIndication,
      sg,
      additionalSocketOptions,
    )
  }

  def unixSocket[F[_]: MonadThrow](
      request: Request[F],
      unixSockets: fs2.io.net.unixsocket.UnixSockets[F],
      address: fs2.io.net.unixsocket.UnixSocketAddress,
      tlsContextOpt: Option[TLSContext[F]],
      enableEndpointValidation: Boolean,
      enableServerNameIndication: Boolean,
  ): Resource[F, RequestKeySocket[F]] = {
    val requestKey = RequestKey.fromRequest(request)
    elevateSocket(
      requestKey,
      unixSockets.client(address),
      tlsContextOpt,
      enableEndpointValidation,
      enableServerNameIndication,
      None,
    )
  }

  def requestKeyToSocketWithKey[F[_]: MonadThrow](
      requestKey: RequestKey,
      tlsContextOpt: Option[TLSContext[F]],
      enableEndpointValidation: Boolean,
      enableServerNameIndication: Boolean,
      sg: SocketGroup[F],
      additionalSocketOptions: List[SocketOption],
  ): Resource[F, RequestKeySocket[F]] =
    Resource
      .eval(getAddress(requestKey))
      .flatMap { address =>
        val s = sg.client(address, options = additionalSocketOptions)
        elevateSocket(
          requestKey,
          s,
          tlsContextOpt,
          enableEndpointValidation,
          enableServerNameIndication,
          Some(address),
        )
      }

  def elevateSocket[F[_]: MonadThrow](
      requestKey: RequestKey,
      initSocket: Resource[F, Socket[F]],
      tlsContextOpt: Option[TLSContext[F]],
      enableEndpointValidation: Boolean,
      enableServerNameIndication: Boolean,
      optionNames: Option[SocketAddress[Host]],
  ): Resource[F, RequestKeySocket[F]] =
    for {
      iSocket <- initSocket
      socket <-
        if (requestKey.scheme === Uri.Scheme.https)
          tlsContextOpt.fold[Resource[F, Socket[F]]](
            Resource.raiseError[F, Socket[F], Throwable](MissingTlsContext())
          )(
            _.clientBuilder(iSocket)
              .withParameters(
                Util.mkClientTLSParameters(
                  optionNames,
                  enableEndpointValidation,
                  enableServerNameIndication,
                )
              )
              .build
              .widen[Socket[F]]
          )
        else iSocket.pure[Resource[F, *]]
    } yield RequestKeySocket(socket, requestKey)

  def request[F[_]: Async](
      request: Request[F],
      connection: EmberConnection[F],
      chunkSize: Int,
      maxResponseHeaderSize: Int,
      idleTimeout: Duration,
      timeout: Duration,
      userAgent: Option[`User-Agent`],
  ): F[(Response[F], F[Option[Array[Byte]]])] = {

    def writeRequestToSocket(req: Request[F], socket: Socket[F]): F[Unit] =
      Encoder
        .reqToBytes(req)
        .through(_.chunks.foreach(c => timeoutMaybe(socket.write(c), idleTimeout)))
        .compile
        .drain

    def writeRead(req: Request[F]): F[(Response[F], F[Option[Array[Byte]]])] =
      writeRequestToSocket(req, connection.keySocket.socket).productR {
        val parse = (
          // leftover bytes from last request
          connection.nextBytes.getAndSet(Array.emptyByteArray),
          // bytes from the pre-emptive read in this connection, which probably has not completed yet, hence the timout
          connection.nextRead.get.flatMap(_.get).rethrow.timeout(idleTimeout),
        ).flatMapN { (head, firstRead) =>
          Parser.Response.parser(maxResponseHeaderSize)(
            firstRead.foldLeft(head)(Util.concatBytes(_, _)),
            timeoutMaybe(connection.keySocket.socket.read(chunkSize), idleTimeout),
          )
        }

        timeoutToMaybe(
          parse,
          timeout,
          Defer[F].defer(
            ApplicativeThrow[F].raiseError(
              new java.util.concurrent.TimeoutException(
                s"Timed Out on EmberClient Header Receive Timeout: $timeout"
              )
            )
          ),
        )
      }

    for {
      processedReq <- preprocessRequest(request, userAgent)
      res <- writeRead(processedReq)
    } yield res
  }.adaptError { case e: EmberException.EmptyStream =>
    new ClosedChannelException() {
      initCause(e)

      override def getMessage(): String =
        "Remote Disconnect: Received zero bytes after sending request"
    }
  }

  private[internal] def preprocessRequest[F[_]: Monad: Clock](
      req: Request[F],
      userAgent: Option[`User-Agent`],
  ): F[Request[F]] = {
    val connection = req.headers
      .get[Connection]
      .fold(Connection(NonEmptyList.of(ci"keep-alive")))(identity)
    val userAgentHeader: Option[`User-Agent`] = req.headers.get[`User-Agent`].orElse(userAgent)
    for {
      date <- req.headers.get[Date].fold(HttpDate.current[F].map(Date(_)))(_.pure[F])
    } yield req.putHeaders(date, connection, userAgentHeader)
  }

  private[ember] def postProcessResponse[F[_]](
      req: Request[F],
      resp: Response[F],
      drain: F[Option[Array[Byte]]],
      nextBytes: Ref[F, Array[Byte]],
      canBeReused: Ref[F, Reusable],
      startNextRead: F[Unit],
  )(implicit F: Concurrent[F]): F[Unit] =
    drain.flatMap {
      case Some(bytes) =>
        val requestClose = connectionFor(req.httpVersion, req.headers).hasClose
        val responseClose = connectionFor(resp.httpVersion, resp.headers).hasClose

        if (requestClose || responseClose) F.unit
        else
          nextBytes.set(bytes) *>
            startNextRead *> // start the next read before returning to pool
            canBeReused.set(Reusable.Reuse) // now it is safe to mark as re-usable
      case None => F.unit
    }

  private def getAddress[F[_]: MonadThrow](requestKey: RequestKey): F[SocketAddress[Host]] =
    requestKey match {
      case RequestKey(s, auth) =>
        val port = auth.port.getOrElse(if (s == Uri.Scheme.https) 443 else 80)
        val host = auth.host.value

        for {
          host <- Host.fromString(host).liftTo[F](MissingHost())
          port <- Port.fromInt(port).liftTo[F](MissingPort())
        } yield SocketAddress[Host](host, port)
    }

  // Assumes that the request doesn't have fancy finalizers besides shutting down the pool
  private[client] def getValidManaged[F[_]: Sync](
      pool: KeyPool[F, RequestKey, EmberConnection[F]],
      request: Request[F],
  ): Resource[F, Managed[F, EmberConnection[F]]] =
    pool.take(RequestKey.fromRequest(request)).flatMap { managed =>
      Resource
        .eval(managed.value.isValid)
        .ifM(
          managed.pure[Resource[F, *]],
          // Already Closed,
          // The Resource Scopes Aren't doing us anything
          // if we have max removed from pool we will need to revisit
          if (managed.isReused) {
            Resource.eval(managed.canBeReused.set(Reusable.DontReuse)) >>
              getValidManaged(pool, request)
          } else
            Resource.eval(
              Sync[F].raiseError(
                new fs2.io.net.SocketException("Fresh connection from pool was not open")
              )
            ),
        )
    }

  private[ember] object RetryLogic {
    private val retryNow = 0.seconds.some
    def retryUntilFresh[F[_]]: RetryPolicy[F] = { (req, result, retries) =>
      if (emberDeadFromPoolPolicy(req, result) && retries <= 2) retryNow
      else None
    }

    def emberDeadFromPoolPolicy[F[_]](
        req: Request[F],
        result: Either[Throwable, Response[F]],
    ): Boolean =
      req.isIdempotent && isRetryableError(result)

    def isRetryableError[F[_]](result: Either[Throwable, Response[F]]): Boolean =
      result match {
        case Right(_) => false
        case Left(_: ClosedChannelException) => true
        case Left(ex: IOException) =>
          val msg = ex.getMessage()
          msg == "Connection reset by peer" || msg == "Broken pipe"
        case _ => false
      }
  }

  private case class MissingHost() extends RuntimeException("Invalid hostname")

  private case class MissingPort() extends RuntimeException("Invalid port")

  private case class MissingTlsContext()
      extends RuntimeException("EmberClient Is Not Configured for Https")
}
