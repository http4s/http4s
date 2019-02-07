package org.http4s
package client
package jetty

import cats.effect._
import cats.effect.implicits._
import cats.implicits.{catsSyntaxEither => _, _}
import fs2._
import fs2.Stream._
import fs2.concurrent.Queue
import java.nio.ByteBuffer
import org.eclipse.jetty.client.api.{Result, Response => JettyResponse}
import org.eclipse.jetty.http.{HttpFields, HttpVersion => JHttpVersion}
import org.eclipse.jetty.util.{Callback => JettyCallback}
import org.http4s.internal.{invokeCallback, loggingAsyncCallback}
import org.log4s.getLogger
import scala.collection.JavaConverters._

private[jetty] final case class ResponseListener[F[_]](
    queue: Queue[F, Option[ByteBuffer]],
    cb: Callback[Resource[F, Response[F]]])(implicit F: ConcurrentEffect[F])
    extends JettyResponse.Listener.Adapter {

  import ResponseListener.logger

  override def onHeaders(response: JettyResponse): Unit = {
    val r = Status
      .fromInt(response.getStatus)
      .map { s =>
        Resource.pure[F, Response[F]](Response(
          status = s,
          httpVersion = getHttpVersion(response.getVersion),
          headers = getHeaders(response.getHeaders),
          body = queue.dequeue.unNoneTerminate.flatMap(bBuf => chunk(Chunk.byteBuffer(bBuf)))
        ))
      }
      .leftMap(t => { abort(t, response); t })

    invokeCallback(logger)(cb(r))
  }

  private def getHttpVersion(version: JHttpVersion): HttpVersion =
    version match {
      case JHttpVersion.HTTP_1_1 => HttpVersion.`HTTP/1.1`
      case JHttpVersion.HTTP_2 => HttpVersion.`HTTP/2.0`
      case JHttpVersion.HTTP_1_0 => HttpVersion.`HTTP/1.0`
      case _ => HttpVersion.`HTTP/1.1`
    }

  private def getHeaders(headers: HttpFields): Headers =
    Headers(headers.asScala.map(h => Header(h.getName, h.getValue)).toList)

  override def onContent(
      response: JettyResponse,
      content: ByteBuffer,
      callback: JettyCallback): Unit = {
    val copy = ByteBuffer.allocate(content.remaining())
    copy.put(content).flip()
    enqueue(copy.some) {
      case Right(_) => IO(callback.succeeded())
      case Left(e) =>
        IO(logger.error(e)("Error in asynchronous callback")) >> IO(callback.failed(e))
    }
  }

  override def onFailure(response: JettyResponse, failure: Throwable): Unit =
    invokeCallback(logger)(cb(Left(failure)))

  // the entire response has been received
  override def onSuccess(response: JettyResponse): Unit =
    closeStream()

  // the entire req/resp conversation has completed
  // (the request might complete after the response has been entirely received)
  override def onComplete(result: Result): Unit =
    if (result.getRequestFailure != null)
      logger.error(result.getRequestFailure)("Failed request")

  private def abort(t: Throwable, response: JettyResponse): Unit =
    if (!response.abort(t)) // this also aborts the request
      logger.error(t)("Failed to abort the response")
    else
      closeStream()

  private def closeStream(): Unit =
    enqueue(None)(loggingAsyncCallback(logger))

  private def enqueue(b: Option[ByteBuffer])(cb: Either[Throwable, Unit] => IO[Unit]): Unit =
    queue.enqueue1(b).runAsync(cb).unsafeRunSync()

}

private[jetty] object ResponseListener {
  private val logger = getLogger

  def apply[F[_]](cb: Callback[Resource[F, Response[F]]])(
      implicit F: ConcurrentEffect[F]): F[ResponseListener[F]] =
    Queue
      .synchronous[F, Option[ByteBuffer]]
      .map(q => ResponseListener(q, cb))
}
