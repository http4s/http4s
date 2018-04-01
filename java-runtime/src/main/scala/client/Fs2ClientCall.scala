package org.lyranthe.fs2_grpc.java_runtime.client

import cats.effect._
import cats.implicits._
import io.grpc.{Metadata, _}
import fs2._

import scala.concurrent.ExecutionContext

case class UnaryResult[A](value: Option[A], status: Option[GrpcStatus])
case class GrpcStatus(status: Status, trailers: Metadata)

class Fs2ClientCall[F[_], Request, Response] private[client] (val call: ClientCall[Request, Response]) extends AnyVal {

  private def halfClose(implicit F: Sync[F]): F[Unit] =
    F.delay(call.halfClose())

  private def request(numMessages: Int)(implicit F: Sync[F]): F[Unit] =
    F.delay(call.request(numMessages))

  private def sendMessage(message: Request)(implicit F: Sync[F]): F[Unit] =
    F.delay(call.sendMessage(message))

  private def start(listener: ClientCall.Listener[Response], metadata: Metadata)(implicit F: Sync[F]): F[Unit] =
    F.delay(call.start(listener, metadata))

  def startListener[A <: ClientCall.Listener[Response]](createListener: F[A], headers: Metadata)(
      implicit F: Sync[F]): F[A] = {
    createListener.flatTap(start(_, headers)) <* request(1)
  }

  def sendSingleMessage(message: Request)(implicit F: Sync[F]): F[Unit] = {
    sendMessage(message) *> halfClose
  }

  def sendStream(stream: Stream[F, Request])(implicit F: Sync[F]): Stream[F, Unit] = {
    stream.evalMap(sendMessage) ++ Stream.eval(halfClose)
  }

  def unaryToUnaryCall(message: Request, headers: Metadata)(implicit F: Async[F], ec: ExecutionContext): F[Response] = {
    for {
      listener <- startListener(Fs2UnaryClientCallListener[F, Response], headers)
      _        <- sendSingleMessage(message)
      result   <- listener.getValue[F]
    } yield result
  }

  def streamingToUnaryCall(messages: Stream[F, Request], headers: Metadata)(implicit F: Effect[F],
                                                                            ec: ExecutionContext): F[Response] = {
    for {
      listener <- startListener(Fs2UnaryClientCallListener[F, Response], headers)
      result   <- Stream.eval(listener.getValue).concurrently(sendStream(messages)).compile.last
    } yield result.get
  }

  def unaryToStreamingCall(message: Request, headers: Metadata)(implicit F: Async[F],
                                                                ec: ExecutionContext): Stream[F, Response] = {
    for {
      listener <- Stream.eval(
        startListener(Fs2StreamClientCallListener[F, Response](call.request), headers) <* sendSingleMessage(message))
      result <- listener.stream[F]
    } yield result
  }

  def streamingToStreamingCall(messages: Stream[F, Request],
                               headers: Metadata)(implicit F: Effect[F], ec: ExecutionContext): Stream[F, Response] = {
    for {
      listener      <- Stream.eval(startListener(Fs2StreamClientCallListener[F, Response](call.request), headers))
      resultOrError <- listener.stream[F].concurrently(sendStream(messages))
    } yield resultOrError
  }
}

object Fs2ClientCall {
  class PartiallyAppliedClientCall[F[_]](val dummy: Boolean = false) extends AnyVal {
    def apply[Request, Response](
        channel: Channel,
        methodDescriptor: MethodDescriptor[Request, Response],
        callOptions: CallOptions)(implicit F: Sync[F]): F[Fs2ClientCall[F, Request, Response]] =
      F.delay(new Fs2ClientCall(channel.newCall[Request, Response](methodDescriptor, callOptions)))
  }

  def apply[F[_]]: PartiallyAppliedClientCall[F] =
    new PartiallyAppliedClientCall[F]
}
