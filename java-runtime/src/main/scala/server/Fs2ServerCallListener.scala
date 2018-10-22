package org.lyranthe.fs2_grpc
package java_runtime
package server

import cats.effect.{Sync, Effect, IO}
import cats.implicits._
import fs2.Stream
import io.grpc.{Metadata, Status, StatusException, StatusRuntimeException}

private[server] trait Fs2ServerCallListener[F[_], G[_], Request, Response] {
  def source: G[Request]
  def call: Fs2ServerCall[F, Request, Response]

  private def reportError(t: Throwable)(implicit F: Sync[F]): F[Unit] = {
    t match {
      case ex: StatusException =>
        call.closeStream(ex.getStatus, Option(ex.getTrailers).getOrElse(new Metadata()))
      case ex: StatusRuntimeException =>
        call.closeStream(ex.getStatus, Option(ex.getTrailers).getOrElse(new Metadata()))
      case ex =>
        // TODO: Customize failure trailers?
        call.closeStream(Status.INTERNAL.withDescription(ex.getMessage).withCause(ex), new Metadata())
    }
  }

  private def handleUnaryResponse(headers: Metadata, response: F[Response])(implicit F: Sync[F]): F[Unit] = {
    ((call.sendHeaders(headers) *> call.request(1) *> response >>= call.sendMessage) *>
      call.closeStream(Status.OK, new Metadata()))
      .handleErrorWith(reportError)
  }

  private def handleStreamResponse(headers: Metadata, response: Stream[F, Response])(implicit F: Sync[F]): F[Unit] =
    (call.sendHeaders(headers) *> call.request(1) *>
      (response.evalMap(call.sendMessage) ++ Stream.eval(call.closeStream(Status.OK, new Metadata()))).compile.drain)
      .handleErrorWith(reportError)

  private def unsafeRun(f: F[Unit])(implicit F: Effect[F]): Unit =
    F.runAsync(f)(_.fold(IO.raiseError, _ => IO.unit)).unsafeRunSync()

  def unsafeUnaryResponse(headers: Metadata, implementation: G[Request] => F[Response])(implicit F: Effect[F]): Unit =
    unsafeRun(handleUnaryResponse(headers, implementation(source)))

  def unsafeStreamResponse(headers: Metadata, implementation: G[Request] => Stream[F, Response])(implicit F: Effect[F]): Unit =
    unsafeRun(handleStreamResponse(headers, implementation(source)))
}
