/*
 * Copyright 2014 http4s.org
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

package org.http4s.blaze.server

import cats.effect._
import cats.syntax.all._
import fs2.concurrent.SignallingRef
import org.http4s._
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.blazecore.websocket.Http4sWSStage
import org.http4s.headers._
import org.http4s.internal.unsafeRunAsync
import org.http4s.websocket.WebSocketHandshake

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets._
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

private[http4s] trait WebSocketSupport[F[_]] extends Http1ServerStage[F] {
  implicit protected val F: ConcurrentEffect[F]

  protected def maxBufferSize: Option[Int]

  override protected def renderResponse(
      req: Request[F],
      resp: Response[F],
      cleanup: () => Future[ByteBuffer],
  ): Unit = {
    val ws = resp.attributes.lookup(org.http4s.server.websocket.websocketKey[F])
    logger.debug(s"Websocket key: $ws\nRequest headers: " + req.headers)

    ws match {
      case None => super.renderResponse(req, resp, cleanup)
      case Some(wsContext) =>
        val hdrs = req.headers.headers.map(h => (h.name.toString, h.value))
        if (WebSocketHandshake.isWebSocketRequest(hdrs))
          WebSocketHandshake.serverHandshake(hdrs) match {
            case Left((code, msg)) =>
              logger.info(s"Invalid handshake $code, $msg")
              unsafeRunAsync {
                wsContext.failureResponse
                  .map(
                    _.withHeaders(
                      Connection.close,
                      "Sec-WebSocket-Version" -> "13",
                    )
                  )
              } {
                case Right(resp) =>
                  IO(super.renderResponse(req, resp, cleanup))
                case Left(_) =>
                  IO.unit
              }

            case Right(hdrs) => // Successful handshake
              val sb = new StringBuilder
              sb.append("HTTP/1.1 101 Switching Protocols\r\n")
              hdrs.foreach { case (k, v) =>
                sb.append(k).append(": ").append(v).append('\r').append('\n')
              }

              wsContext.headers.foreach { hdr =>
                sb.append(hdr.name).append(": ").append(hdr.value).append('\r').append('\n')
                ()
              }

              sb.append('\r').append('\n')

              // write the accept headers and reform the pipeline
              channelWrite(ByteBuffer.wrap(sb.result().getBytes(ISO_8859_1))).onComplete {
                case Success(_) =>
                  logger.debug("Switching pipeline segments for websocket")

                  val deadSignal = F.toIO(SignallingRef[F, Boolean](false)).unsafeRunSync()
                  val sentClose = new AtomicBoolean(false)
                  val segment =
                    LeafBuilder(new Http4sWSStage[F](wsContext.webSocket, sentClose, deadSignal))
                      .prepend(new WSFrameAggregator)
                      .prepend(new WebSocketDecoder(maxBufferSize.getOrElse(0)))

                  this.replaceTail(segment, true)

                case Failure(t) => fatalError(t, "Error writing Websocket upgrade response")
              }(executionContext)
          }
        else super.renderResponse(req, resp, cleanup)
    }
  }
}
