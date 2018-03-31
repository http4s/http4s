package org.lyranthe.fs2_grpc.java_runtime.server

import cats.effect.{Effect, IO, Sync}
import cats.implicits._
import fs2.Stream
import io.grpc.{Metadata, Status, StatusException, StatusRuntimeException}

private[server] trait Fs2ServerCallListener[F[_], G[_], Request, Response] {
  def source: G[Request]
  def call: Fs2ServerCall[F, Request, Response]

  def reportError(t: Throwable)(implicit F: Sync[F]): F[Unit] = {
    t match {
      case ex: StatusException =>
        call.closeStream(ex.getStatus, ex.getTrailers)
      case ex: StatusRuntimeException =>
        call.closeStream(ex.getStatus, ex.getTrailers)
      case ex =>
        // TODO: Customize failure trailers?
        call.closeStream(Status.INTERNAL.withDescription(ex.getMessage).withCause(ex), new Metadata())
    }
  }

  def handleUnaryResponse(headers: Metadata, response: F[Response])(implicit F: Sync[F]): F[Unit] = {
    ((call.sendHeaders(headers) *> call.request(1) *> response >>= call.sendMessage) *>
      call.closeStream(Status.OK, new Metadata()))
      .handleErrorWith(reportError)
  }

  def handleStreamResponse(headers: Metadata, response: Stream[F, Response])(implicit F: Sync[F]): F[Unit] =
    (call.sendHeaders(headers) *> call.request(1) *>
      (response.evalMap(call.sendMessage) ++ Stream.eval(call.closeStream(Status.OK, new Metadata()))).compile.drain)
      .handleErrorWith(reportError)

  def unsafeRun(f: F[Unit])(implicit F: Effect[F]): Unit =
    F.runAsync(f)(_.fold(IO.raiseError, _ => IO.unit)).unsafeRunSync()

  def unsafeUnaryResponse(headers: Metadata, implementation: G[Request] => F[Response])(implicit F: Effect[F]): Unit =
    unsafeRun(handleUnaryResponse(headers, implementation(source)))

  def unsafeStreamResponse(headers: Metadata, implementation: G[Request] => Stream[F, Response])(
      implicit F: Effect[F]): Unit =
    unsafeRun(handleStreamResponse(headers, implementation(source)))
}
