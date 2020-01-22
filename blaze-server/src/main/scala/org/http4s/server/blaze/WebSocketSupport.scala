package org.http4s.server.blaze

import cats.effect._
import cats.implicits._
import fs2.concurrent.{Queue, SignallingRef}
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets._
import java.util.concurrent.atomic.AtomicBoolean

import org.http4s._
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.blazecore.websocket.Http4sWSStage
import org.http4s.headers._
import org.http4s.internal.unsafeRunAsync
import org.http4s.syntax.string._
import org.http4s.websocket.{WebSocketFrame, WebSocketHandshake}

import scala.concurrent.Future
import scala.util.{Failure, Success}

private[blaze] trait WebSocketSupport[F[_]] extends Http1ServerStage[F] {
  protected implicit def F: ConcurrentEffect[F]

  override protected def renderResponse(
      req: Request[F],
      resp: Response[F],
      cleanup: () => Future[ByteBuffer]): Unit = {
    val ws = resp.attributes.lookup(org.http4s.server.websocket.websocketKey[F])
    logger.debug(s"Websocket key: $ws\nRequest headers: " + req.headers)

    ws match {
      case None => super.renderResponse(req, resp, cleanup)
      case Some(wsContext) =>
        val hdrs = req.headers.toList.map(h => (h.name.toString, h.value))
        if (WebSocketHandshake.isWebSocketRequest(hdrs)) {
          WebSocketHandshake.serverHandshake(hdrs) match {
            case Left((code, msg)) =>
              logger.info(s"Invalid handshake $code, $msg")
              unsafeRunAsync {
                wsContext.failureResponse
                  .map(
                    _.withHeaders(
                      Connection("close".ci),
                      Header.Raw(headers.`Sec-WebSocket-Version`.name, "13")
                    ))
              } {
                case Right(resp) =>
                  IO(super.renderResponse(req, resp, cleanup))
                case Left(_) =>
                  IO.unit
              }

            case Right(hdrs) => // Successful handshake
              val sb = new StringBuilder
              sb.append("HTTP/1.1 101 Switching Protocols\r\n")
              hdrs.foreach {
                case (k, v) => sb.append(k).append(": ").append(v).append('\r').append('\n')
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
                  val sinkQueue = F.toIO(Queue.unbounded[F, WebSocketFrame]).unsafeRunSync()
                  val sentClose = new AtomicBoolean(false)
                  val segment =
                    LeafBuilder(new Http4sWSStage[F](wsContext.webSocket, sentClose, deadSignal, sinkQueue))
                      .prepend(new WSFrameAggregator)
                      .prepend(new WebSocketDecoder)

                  this.replaceTail(segment, true)

                case Failure(t) => fatalError(t, "Error writing Websocket upgrade response")
              }(executionContext)
          }
        } else super.renderResponse(req, resp, cleanup)
    }
  }
}
