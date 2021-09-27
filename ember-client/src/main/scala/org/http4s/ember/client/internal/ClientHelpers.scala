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

import org.http4s.ember.client._
import fs2.io.tcp._
import cats._
import cats.data.NonEmptyList
import cats.effect._
import cats.effect.implicits._
import cats.effect.concurrent._
import cats.syntax.all._
import scala.annotation.nowarn
import scala.concurrent.duration._
import java.net.InetSocketAddress
import org.http4s._
import org.http4s.implicits._
import org.http4s.client.RequestKey
import _root_.org.http4s.ember.core.{Encoder, Parser}
import _root_.fs2.io.tcp.SocketGroup
import _root_.fs2.io.tls._
import _root_.io.chrisdavenport.keypool._
import javax.net.ssl.SNIHostName
import org.http4s.headers.{Connection, Date, `User-Agent`}
import _root_.org.http4s.ember.core.Util.durationToFinite
import java.nio.channels.ClosedChannelException

private[client] object ClientHelpers {

  def requestToSocketWithKey[F[_]: Concurrent: Timer: ContextShift](
      request: Request[F],
      tlsContextOpt: Option[TLSContext],
      enableEndpointValiation: Boolean,
      sg: SocketGroup,
      additionalSocketOptions: List[SocketOptionMapping[_]]
  ): Resource[F, RequestKeySocket[F]] = {
    val requestKey = RequestKey.fromRequest(request)
    requestKeyToSocketWithKey[F](
      requestKey,
      tlsContextOpt,
      enableEndpointValiation,
      sg,
      additionalSocketOptions
    )
  }

  @nowarn("cat=unused")
  def requestKeyToSocketWithKey[F[_]: Concurrent: Timer: ContextShift](
      requestKey: RequestKey,
      tlsContextOpt: Option[TLSContext],
      enableEndpointValiation: Boolean,
      sg: SocketGroup,
      additionalSocketOptions: List[SocketOptionMapping[_]]
  ): Resource[F, RequestKeySocket[F]] =
    for {
      address <- Resource.eval(getAddress(requestKey))
      initSocket <- sg.client[F](address, additionalSocketOptions = additionalSocketOptions)
      socket <- {
        if (requestKey.scheme === Uri.Scheme.https)
          tlsContextOpt.fold[Resource[F, Socket[F]]] {
            ApplicativeThrow[Resource[F, *]].raiseError(
              new Throwable("EmberClient Not Configured for Https")
            )
          } { tlsContext =>
            tlsContext
              .client(
                initSocket,
                TLSParameters(
                  serverNames = Some(List(new SNIHostName(address.getHostName))),
                  endpointIdentificationAlgorithm =
                    if (enableEndpointValiation) Some("HTTPS") else None)
              )
              .widen[Socket[F]]
          }
        else initSocket.pure[Resource[F, *]]
      }
    } yield RequestKeySocket(socket, requestKey)

  @nowarn("cat=unused")
  def request[F[_]: Concurrent: ContextShift: Timer](
      request: Request[F],
      connection: EmberConnection[F],
      chunkSize: Int,
      maxResponseHeaderSize: Int,
      idleTimeout: Duration,
      timeout: Duration,
      userAgent: Option[`User-Agent`]
  ): F[(Response[F], F[Option[Array[Byte]]])] = {

    def writeRequestToSocket(
        req: Request[F],
        socket: Socket[F],
        timeout: Option[FiniteDuration]): F[Unit] =
      Encoder
        .reqToBytes(req)
        .through(socket.writes(timeout))
        .compile
        .drain

    def writeRead(req: Request[F]): F[(Response[F], F[Option[Array[Byte]]])] =
      writeRequestToSocket(req, connection.keySocket.socket, durationToFinite(idleTimeout)) >>
        connection.nextBytes.getAndSet(Array.emptyByteArray).flatMap { head =>
          val finiteDuration = durationToFinite(timeout)
          val parse = Parser.Response.parser(maxResponseHeaderSize)(
            head,
            connection.keySocket.socket.read(chunkSize, durationToFinite(idleTimeout))
          )

          finiteDuration.fold(parse)(duration =>
            parse.timeoutTo(
              duration,
              ApplicativeThrow[F].raiseError(new java.util.concurrent.TimeoutException(
                s"Timed Out on EmberClient Header Receive Timeout: $duration"))))
        }

    for {
      processedReq <- preprocessRequest(request, userAgent)
      res <- writeRead(processedReq)
    } yield res
  }.adaptError { case e @ org.http4s.ember.core.EmptyStreamError() =>
    new ClosedChannelException() {
      initCause(e)

      override def getMessage(): String =
        "Remote Disconnect: Received zero bytes after sending request"
    }
  }

  private[internal] def preprocessRequest[F[_]: Monad: Clock](
      req: Request[F],
      userAgent: Option[`User-Agent`]): F[Request[F]] = {
    val connection = req.headers
      .get(Connection)
      .fold(Connection(NonEmptyList.of("keep-alive".ci)))(identity)
    val userAgentHeader: Option[`User-Agent`] = req.headers.get(`User-Agent`).orElse(userAgent)
    for {
      date <- req.headers.get(Date).fold(HttpDate.current[F].map(Date(_)))(_.pure[F])
    } yield req
      .putHeaders(date, connection)
      .putHeaders(userAgentHeader.toSeq: _*)
  }

  private[ember] def postProcessResponse[F[_]](
      req: Request[F],
      resp: Response[F],
      drain: F[Option[Array[Byte]]],
      nextBytes: Ref[F, Array[Byte]],
      canBeReused: Ref[F, Reusable])(implicit F: Concurrent[F]): F[Unit] =
    drain.flatMap {
      case Some(bytes) =>
        val requestClose = req.headers.get(Connection).exists(_.hasClose)
        val responseClose = resp.headers.get(Connection).exists(_.hasClose)

        if (requestClose || responseClose) F.unit
        else nextBytes.set(bytes) >> canBeReused.set(Reusable.Reuse)
      case None => F.unit
    }

  // https://github.com/http4s/http4s/blob/main/blaze-client/src/main/scala/org/http4s/client/blaze/Http1Support.scala#L86
  private def getAddress[F[_]: Sync](requestKey: RequestKey): F[InetSocketAddress] =
    requestKey match {
      case RequestKey(s, auth) =>
        val port = auth.port.getOrElse(if (s == Uri.Scheme.https) 443 else 80)
        val host = auth.host.value
        Sync[F].delay(new InetSocketAddress(host, port))
    }

  // Assumes that the request doesn't have fancy finalizers besides shutting down the pool
  private[client] def getValidManaged[F[_]: Sync](
      pool: KeyPool[F, RequestKey, EmberConnection[F]],
      request: Request[F]): Resource[F, Managed[F, EmberConnection[F]]] =
    pool.take(RequestKey.fromRequest(request)).flatMap { managed =>
      Resource
        .eval(managed.value.keySocket.socket.isOpen)
        .ifM(
          managed.pure[Resource[F, *]],
          // Already Closed,
          // The Resource Scopes Aren't doing us anything
          // if we have max removed from pool we will need to revisit
          if (managed.isReused) {
            Resource.eval(managed.canBeReused.set(Reusable.DontReuse)) >>
              getValidManaged(pool, request)
          } else
            Resource.eval(Sync[F].raiseError(
              new java.net.SocketException("Fresh connection from pool was not open")))
        )
    }

  private[ember] object RetryLogic {
    import org.http4s.client.middleware._
    import org.http4s.syntax.all._
    // import org.http4s.headers.`Idempotency-Key`

    private val retryNow = 0.seconds.some
    def retryUntilFresh[F[_]]: RetryPolicy[F] = { (req, result, retries) =>
      if (emberDeadFromPoolPolicy(req, result) && retries <= 2) retryNow
      else None
    }

    def emberDeadFromPoolPolicy[F[_]](
        req: Request[F],
        result: Either[Throwable, Response[F]]): Boolean =
      (req.method.isIdempotent || req.headers.get("idempotency-key".ci).isDefined) &&
        isEmptyStreamError(result)

    def isEmptyStreamError[F[_]](result: Either[Throwable, Response[F]]): Boolean =
      result match {
        case Right(_) => false
        case Left(_: ClosedChannelException) => true
        case _ => false
      }
  }
}
