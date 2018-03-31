package org.lyranthe.grpc.java_runtime.server

import cats.effect.Effect
import cats.implicits._
import fs2._
import io.grpc._

import scala.concurrent.ExecutionContext

class Fs2ServerCallHandler[F[_]](val dummy: Boolean = false)
    extends AnyVal {
  def unaryToUnary[Request, Response](implementation: Request => F[Response])(
      implicit F: Effect[F],
      ec: ExecutionContext): ServerCallHandler[Request, Response] =
    (call: ServerCall[Request, Response], headers: Metadata) => {
      val listener = Fs2UnaryServerCallListener[F].unsafeCreate(call)
      listener.unsafeUnaryResponse(new Metadata(), _ flatMap implementation)
      listener
    }

  def unaryToStream[Request, Response](
      implementation: Request => Stream[F, Response])(
      implicit F: Effect[F],
      ec: ExecutionContext): ServerCallHandler[Request, Response] =
    (call: ServerCall[Request, Response], headers: Metadata) => {
      val listener = Fs2UnaryServerCallListener[F].unsafeCreate(call)
      listener.unsafeStreamResponse(headers,
                                    v => Stream.eval(v) >>= implementation)
      listener
    }

  def streamToUnary[Request, Response](
      implementation: Stream[F, Request] => F[Response])(
      implicit F: Effect[F],
      ec: ExecutionContext): ServerCallHandler[Request, Response] =
    (call: ServerCall[Request, Response], headers: Metadata) => {
      val listener = Fs2StreamServerCallListener[F].unsafeCreate(call)
      listener.unsafeUnaryResponse(headers, implementation)
      listener
    }

  def streamToStream[Request, Response](
      implementation: Stream[F, Request] => Stream[F, Response])(
      implicit F: Effect[F],
      ec: ExecutionContext): ServerCallHandler[Request, Response] =
    (call: ServerCall[Request, Response], headers: Metadata) => {
      val listener = Fs2StreamServerCallListener[F].unsafeCreate(call)
      listener.unsafeStreamResponse(headers, implementation)
      listener
    }
}

object Fs2ServerCallHandler {
  def apply[F[_]]: Fs2ServerCallHandler[F] = new Fs2ServerCallHandler[F]
}
