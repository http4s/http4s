package org.lyranthe.grpc.java_runtime.server

import cats.effect._
import io.grpc._

// TODO: Add attributes, compression, message compression.
private[server] class Fs2ServerCall[F[_], Request, Response] private (val call: ServerCall[Request, Response])
    extends AnyVal {
  def sendHeaders(headers: Metadata)(implicit F: Sync[F]): F[Unit] =
    F.delay(call.sendHeaders(headers))

  def closeStream(status: Status, trailers: Metadata)(implicit F: Sync[F]): F[Unit] =
    F.delay(call.close(status, trailers))

  def sendMessage(message: Response)(implicit F: Sync[F]): F[Unit] =
    F.delay(call.sendMessage(message))

  def request(numMessages: Int)(implicit F: Sync[F]): F[Unit] =
    F.delay(call.request(numMessages))
}

private[server] object Fs2ServerCall {
  def apply[F[_], Request, Response](call: ServerCall[Request, Response]): Fs2ServerCall[F, Request, Response] =
    new Fs2ServerCall[F, Request, Response](call)
}
