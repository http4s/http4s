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

package org.http4s.ember.server.internal

import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import fs2.io.net.Socket
import fs2.io.net.tls.TLSParameters
import org.http4s.HttpApp
import org.http4s.ember.core.h2.H2Frame
import org.http4s.ember.core.h2.H2Server
import org.http4s.ember.core.h2.H2TLS
import org.typelevel.log4cats.Logger
import scodec.bits.ByteVector

import scala.concurrent.duration.Duration

/** The operations the server needs from the HTTP/2 implementation. */
private[ember] trait Http2Support[F[_]] {

  /** Adds h2 to the ALPN protocols of the TLS parameters. */
  def transformTls(params: TLSParameters): TLSParameters

  /** Reads the connection preface. `Left` carries the bytes read if it is not the preface. */
  def checkPreface(socket: Socket[F], timeout: Duration)(implicit
      F: Async[F]
  ): F[Either[ByteVector, Unit]]

  /** Like [[checkPreface]], but fails if the preface is not there. */
  def requirePreface(socket: Socket[F], timeout: Duration)(implicit F: Async[F]): F[Unit]

  /** Serves the socket as HTTP/2, after the connection preface. */
  def serve(
      socket: Socket[F],
      httpApp: HttpApp[F],
      requestHeaderReceiveTimeout: Duration,
      idleTimeout: Duration,
      maxHeaderSize: Int,
      logger: Logger[F],
  )(implicit F: Async[F]): Resource[F, Unit]
}

private[ember] object Http2Support {

  /** The only place that instantiates the HTTP/2 implementation. */
  def default[F[_]]: Http2Support[F] = new Default[F]

  private final class Default[F[_]] extends Http2Support[F] {
    def transformTls(params: TLSParameters): TLSParameters =
      H2TLS.transform(params)

    def checkPreface(socket: Socket[F], timeout: Duration)(implicit
        F: Async[F]
    ): F[Either[ByteVector, Unit]] =
      H2Server.checkConnectionPreface(socket, timeout)

    def requirePreface(socket: Socket[F], timeout: Duration)(implicit F: Async[F]): F[Unit] =
      H2Server.requireConnectionPreface(socket, timeout)

    def serve(
        socket: Socket[F],
        httpApp: HttpApp[F],
        requestHeaderReceiveTimeout: Duration,
        idleTimeout: Duration,
        maxHeaderSize: Int,
        logger: Logger[F],
    )(implicit F: Async[F]): Resource[F, Unit] = {
      val settings = H2Frame.Settings.ConnectionSettings.default
        .copy(maxHeaderListSize = Some(H2Frame.Settings.SettingsMaxHeaderListSize(maxHeaderSize)))
      H2Server.fromSocket[F](
        socket,
        httpApp,
        requestHeaderReceiveTimeout,
        idleTimeout,
        settings,
        logger,
      )
    }
  }
}
