package org.http4s.server.blaze

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets._

import fs2.Strategy
import org.http4s.headers._
import org.http4s._
import org.http4s.blaze.http.websocket.{WSFrameAggregator, WebSocketDecoder}
import org.http4s.websocket.WebsocketHandshake
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.blaze.websocket.Http4sWSStage
import org.http4s.syntax.string._

import scala.util.{Failure, Success}
import scala.concurrent.Future

private trait WebSocketSupport extends Http1ServerStage {
  override protected def renderResponse(req: Request, maybeResponse: MaybeResponse, cleanup: () => Future[ByteBuffer]): Unit = {
    val resp = maybeResponse.orNotFound
    val ws = resp.attributes.get(org.http4s.server.websocket.websocketKey)
    logger.debug(s"Websocket key: $ws\nRequest headers: " + req.headers)

    if (ws.isDefined) {
      val hdrs =  req.headers.map(h=>(h.name.toString,h.value))
      if (WebsocketHandshake.isWebSocketRequest(hdrs)) {
        WebsocketHandshake.serverHandshake(hdrs) match {
          case Left((code, msg)) =>
            logger.info(s"Invalid handshake $code, $msg")
            val resp = Response(Status.BadRequest)
              .withBody(msg)
              .map(_.replaceAllHeaders(
                 Connection("close".ci),
                 Header.Raw(headers.`Sec-WebSocket-Version`.name, "13")
              )).unsafeRun

            super.renderResponse(req, resp, cleanup)

          case Right(hdrs) =>  // Successful handshake
            val sb = new StringBuilder
            sb.append("HTTP/1.1 101 Switching Protocols\r\n")
            hdrs.foreach { case (k, v) => sb.append(k).append(": ").append(v).append('\r').append('\n') }
            sb.append('\r').append('\n')

            // write the accept headers and reform the pipeline
            channelWrite(ByteBuffer.wrap(sb.result().getBytes(ISO_8859_1))).onComplete {
              case Success(_) =>
                logger.debug("Switching pipeline segments for websocket")

                val segment = LeafBuilder(new Http4sWSStage(ws.get)(Strategy.fromExecutor(ec)))
                              .prepend(new WSFrameAggregator)
                              .prepend(new WebSocketDecoder(false))

                this.replaceInline(segment)

              case Failure(t) => fatalError(t, "Error writing Websocket upgrade response")
            }(ec)
        }

      } else super.renderResponse(req, resp, cleanup)
    } else super.renderResponse(req, resp, cleanup)
  }
}
