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

package org.http4s
package blaze
package server

import cats.effect.Async
import cats.effect.std.Dispatcher
import cats.syntax.all._
import fs2.Stream._
import fs2._
import java.util.Locale
import java.util.concurrent.TimeoutException
import org.http4s.blaze.http.http2._
import org.http4s.blaze.http.{HeaderNames, Headers}
import org.http4s.blaze.pipeline.{TailStage, Command => Cmd}
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.blazecore.IdleTimeoutStage
import org.http4s.blazecore.util.{End, Http2Writer}
import org.http4s.server.ServiceErrorHandler
import org.http4s.{Method => HMethod}
import org.typelevel.vault._
import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util._

private class Http2NodeStage[F[_]](
    streamId: Int,
    timeout: Duration,
    implicit private val executionContext: ExecutionContext,
    attributes: () => Vault,
    httpApp: HttpApp[F],
    serviceErrorHandler: ServiceErrorHandler[F],
    responseHeaderTimeout: Duration,
    idleTimeout: Duration,
    scheduler: TickWheelExecutor,
    dispatcher: Dispatcher[F])(implicit F: Async[F])
    extends TailStage[StreamFrame] {
  // micro-optimization: unwrap the service and call its .run directly
  private[this] val runApp = httpApp.run

  override def name = "Http2NodeStage"

  override protected def stageStartup(): Unit = {
    super.stageStartup()
    initIdleTimeout()
    readHeaders()
  }

  private def initIdleTimeout() =
    idleTimeout match {
      case f: FiniteDuration =>
        val cb: Callback[TimeoutException] = {
          case Left(t) =>
            logger.error(t)("Error in idle timeout callback")
            closePipeline(Some(t))
          case Right(_) =>
            logger.debug("Shutting down due to idle timeout")
            closePipeline(None)
        }
        val stage = new IdleTimeoutStage[StreamFrame](f, scheduler, executionContext)
        spliceBefore(stage)
        stage.init(cb)
      case _ =>
    }

  private def readHeaders(): Unit =
    channelRead(timeout = timeout).onComplete {
      case Success(HeadersFrame(_, endStream, hs)) =>
        checkAndRunRequest(hs, endStream)

      case Success(frame) =>
        val e = Http2Exception.PROTOCOL_ERROR.rst(streamId, s"Received invalid frame: $frame")
        closePipeline(Some(e))

      case Failure(Cmd.EOF) =>
        closePipeline(None)

      case Failure(t) =>
        logger.error(t)("Unknown error in readHeaders")
        val e = Http2Exception.INTERNAL_ERROR.rst(streamId, s"Unknown error")
        closePipeline(Some(e))
    }

  /** collect the body: a maxlen < 0 is interpreted as undefined */
  private def getBody(maxlen: Long): EntityBody[F] = {
    var complete = false
    var bytesRead = 0L

    val t = F.async[Option[Chunk[Byte]]] { cb =>
      F.delay {
        if (complete) cb(End)
        else
          channelRead(timeout = timeout).onComplete {
            case Success(DataFrame(last, bytes)) =>
              complete = last
              bytesRead += bytes.remaining()

              // Check length: invalid length is a stream error of type PROTOCOL_ERROR
              // https://tools.ietf.org/html/draft-ietf-httpbis-http2-17#section-8.1.2  -> 8.2.1.6
              if (complete && maxlen > 0 && bytesRead != maxlen) {
                val msg = s"Entity too small. Expected $maxlen, received $bytesRead"
                val e = Http2Exception.PROTOCOL_ERROR.rst(streamId, msg)
                closePipeline(Some(e))
                cb(Either.left(InvalidBodyException(msg)))
              } else if (maxlen > 0 && bytesRead > maxlen) {
                val msg = s"Entity too large. Expected $maxlen, received bytesRead"
                val e = Http2Exception.PROTOCOL_ERROR.rst(streamId, msg)
                closePipeline(Some(e))
                cb(Either.left(InvalidBodyException(msg)))
              } else cb(Either.right(Some(Chunk.array(bytes.array))))

            case Success(HeadersFrame(_, true, ts)) =>
              logger.warn("Discarding trailers: " + ts)
              cb(Either.right(Some(Chunk.empty)))

            case Success(other) => // This should cover it
              val msg = "Received invalid frame while accumulating body: " + other
              logger.info(msg)
              val e = Http2Exception.PROTOCOL_ERROR.rst(streamId, msg)
              closePipeline(Some(e))
              cb(Either.left(InvalidBodyException(msg)))

            case Failure(Cmd.EOF) =>
              logger.debug("EOF while accumulating body")
              cb(Either.left(InvalidBodyException("Received premature EOF.")))
              closePipeline(None)

            case Failure(t) =>
              logger.error(t)("Error in getBody().")
              val e = Http2Exception.INTERNAL_ERROR.rst(streamId, "Failed to read body")
              cb(Either.left(e))
              closePipeline(Some(e))
          }

        None
      }
    }

    repeatEval(t).unNoneTerminate.flatMap(chunk(_).covary[F])
  }

  private def checkAndRunRequest(hs: Headers, endStream: Boolean): Unit = {
    val headers = new ListBuffer[Header.ToRaw]
    var method: HMethod = null
    var scheme: String = null
    var path: Uri = null
    var contentLength: Long = -1
    var error: String = ""
    var pseudoDone = false

    hs.foreach {
      case (PseudoHeaders.Method, v) =>
        if (pseudoDone) error += "Pseudo header in invalid position. "
        else if (method == null) org.http4s.Method.fromString(v) match {
          case Right(m) => method = m
          case Left(e) => error = s"$error Invalid method: $e "
        }
        else error += "Multiple ':method' headers defined. "

      case (PseudoHeaders.Scheme, v) =>
        if (pseudoDone) error += "Pseudo header in invalid position. "
        else if (scheme == null) scheme = v
        else error += "Multiple ':scheme' headers defined. "

      case (PseudoHeaders.Path, v) =>
        if (pseudoDone) error += "Pseudo header in invalid position. "
        else if (path == null) Uri.requestTarget(v) match {
          case Right(p) => path = p
          case Left(e) => error = s"$error Invalid path: $e"
        }
        else error += "Multiple ':path' headers defined. "

      case (PseudoHeaders.Authority, _) => // NOOP; TODO: we should keep the authority header
        if (pseudoDone) error += "Pseudo header in invalid position. "

      case h @ (k, _) if k.startsWith(":") => error += s"Invalid pseudo header: $h. "
      case (k, _) if !HeaderNames.validH2HeaderKey(k) => error += s"Invalid header key: $k. "

      case hs => // Non pseudo headers
        pseudoDone = true
        hs match {
          case h @ (HeaderNames.Connection, _) =>
            error += s"HTTP/2 forbids connection specific headers: $h. "

          case (HeaderNames.ContentLength, v) =>
            if (contentLength < 0) try {
              val sz = java.lang.Long.parseLong(v)
              if (sz != 0 && endStream) error += s"Nonzero content length ($sz) for end of stream."
              else if (sz < 0) error += s"Negative content length: $sz"
              else contentLength = sz
            } catch {
              case _: NumberFormatException => error += s"Invalid content-length: $v. "
            }
            else
              error += "Received multiple content-length headers"

          case (HeaderNames.TE, v) =>
            if (!v.equalsIgnoreCase("trailers"))
              error += s"HTTP/2 forbids TE header values other than 'trailers'. "
          // ignore otherwise

          case (k, v) => headers += k -> v
        }
    }

    if (method == null || scheme == null || path == null)
      error += s"Invalid request: missing pseudo headers. Method: $method, Scheme: $scheme, path: $path. "

    if (error.length > 0)
      closePipeline(Some(Http2Exception.PROTOCOL_ERROR.rst(streamId, error)))
    else {
      val body = if (endStream) EmptyBody else getBody(contentLength)
      val hs = Headers(headers.result())
      val req = Request(method, path, HttpVersion.`HTTP/2`, hs, body, attributes())
      executionContext.execute(new Runnable {
        def run(): Unit = {
          val action = F
            .defer(raceTimeout(req))
            .recoverWith(serviceErrorHandler(req))
            .flatMap(renderResponse(_))

          val fa = action.attempt.flatMap {
            case Right(_) => F.unit
            case Left(t) =>
              F.delay(logger.error(t)(s"Error running request: $req")).attempt *> F.delay(
                closePipeline(None))
          }

          dispatcher.unsafeRunSync(fa)

          ()
        }
      })
    }
  }

  private def renderResponse(resp: Response[F]): F[Unit] = {
    val hs = new ArrayBuffer[(String, String)](16)
    hs += PseudoHeaders.Status -> Integer.toString(resp.status.code)
    resp.headers.foreach { h =>
      // Connection related headers must be removed from the message because
      // this information is conveyed by other means.
      // http://httpwg.org/specs/rfc7540.html#rfc.section.8.1.2
      if (h.name != headers.`Transfer-Encoding`.name &&
        h.name != Header[headers.Connection].name) {
        hs += ((h.name.toString.toLowerCase(Locale.ROOT), h.value))
        ()
      }
    }

    new Http2Writer(this, hs, executionContext).writeEntityBody(resp.body).attempt.map {
      case Right(_) => closePipeline(None)
      case Left(Cmd.EOF) => stageShutdown()
      case Left(t) => closePipeline(Some(t))
    }
  }

  private[this] val raceTimeout: Request[F] => F[Response[F]] =
    responseHeaderTimeout match {
      case finite: FiniteDuration =>
        val timeoutResponse = F.sleep(finite).as(Response.timeout[F])
        req => F.race(runApp(req), timeoutResponse).map(_.merge)
      case _ =>
        runApp
    }
}
