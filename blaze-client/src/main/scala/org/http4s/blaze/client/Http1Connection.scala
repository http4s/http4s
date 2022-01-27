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
package client

import cats.effect.implicits._
import cats.effect.kernel.Async
import cats.effect.kernel.Deferred
import cats.effect.kernel.Outcome
import cats.effect.kernel.Resource
import cats.effect.std.Dispatcher
import cats.syntax.all._
import fs2._
import org.http4s.Uri.Authority
import org.http4s.Uri.RegName
import org.http4s.blaze.pipeline.Command.EOF
import org.http4s.blazecore.Http1Stage
import org.http4s.blazecore.IdleTimeoutStage
import org.http4s.blazecore.util.Http1Writer
import org.http4s.client.RequestKey
import org.http4s.headers.Host
import org.http4s.headers.`Content-Length`
import org.http4s.headers.`User-Agent`
import org.http4s.headers.{Connection => HConnection}
import org.http4s.internal.CharPredicate
import org.http4s.util.StringWriter
import org.http4s.util.Writer
import org.typelevel.vault._

import java.net.SocketException
import java.nio.ByteBuffer
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

private final class Http1Connection[F[_]](
    val requestKey: RequestKey,
    override protected val executionContext: ExecutionContext,
    maxResponseLineSize: Int,
    maxHeaderLength: Int,
    maxChunkSize: Int,
    override val chunkBufferMaxSize: Int,
    parserMode: ParserMode,
    userAgent: Option[`User-Agent`],
    idleTimeoutStage: Option[IdleTimeoutStage[ByteBuffer]],
    override val dispatcher: Dispatcher[F],
)(implicit protected val F: Async[F])
    extends Http1Stage[F]
    with BlazeConnection[F] {
  import Http1Connection._
  import Resource.ExitCase

  override def name: String = getClass.getName
  private val parser =
    new BlazeHttp1ClientParser(maxResponseLineSize, maxHeaderLength, maxChunkSize, parserMode)

  private val stageState = new AtomicReference[State](ReadIdle(None))
  private val closed = Deferred.unsafe[F, Unit]

  override def isClosed: Boolean =
    stageState.get match {
      case Error(_) => true
      case _ => false
    }

  override def isRecyclable: F[Boolean] =
    F.delay(stageState.get match {
      case ReadIdle(_) => true
      case _ => false
    })

  override def shutdown(): Unit = stageShutdown()

  override def stageShutdown(): Unit = shutdownWithError(EOF)

  override protected def fatalError(t: Throwable, msg: String): Unit = {
    val realErr = t match {
      case _: TimeoutException => EOF
      case EOF => EOF
      case t =>
        logger.error(t)(s"Fatal Error: $msg")
        t
    }
    shutdownWithError(realErr)
  }

  @tailrec
  private def shutdownWithError(t: Throwable): Unit =
    stageState.get match {
      // If we have a real error, lets put it here.
      case st @ Error(EOF) if t != EOF =>
        if (!stageState.compareAndSet(st, Error(t))) shutdownWithError(t)
        else {
          closePipeline(Some(t))
        }

      case Error(_) => // NOOP: already shut down
      case x =>
        if (!stageState.compareAndSet(x, Error(t))) shutdownWithError(t)
        else {
          val cmd = t match {
            case EOF => None
            case _ => Some(t)
          }
          closePipeline(cmd)
          super.stageShutdown()
          dispatcher.unsafeRunAndForget(closed.complete(()))
        }
    }

  @tailrec
  def resetRead(): Unit = {
    val state = stageState.get()
    val nextState = state match {
      case ReadActive =>
        // idleTimeout is activated when entering ReadWrite state, remains active throughout Read and Write and is deactivated when entering the Idle state
        idleTimeoutStage.foreach(_.cancelTimeout())
        Some(ReadIdle(Some(startIdleRead())))
      case _ => None
    }

    nextState match {
      case Some(n) => if (stageState.compareAndSet(state, n)) parser.reset() else resetRead()
      case None => ()
    }
  }

  // #4798 We read from the channel while the connection is idle, in order to receive an EOF when the connection gets closed.
  private def startIdleRead(): Future[ByteBuffer] = {
    val f = channelRead()
    f.onComplete {
      case Failure(t) => shutdownWithError(t)
      case _ =>
    }(executionContext)
    f
  }

  def runRequest(req: Request[F], cancellation: F[TimeoutException]): F[Resource[F, Response[F]]] =
    F.defer[Resource[F, Response[F]]] {
      stageState.get match {
        case i @ ReadIdle(idleRead) =>
          if (stageState.compareAndSet(i, ReadActive)) {
            logger.debug(s"Connection was idle. Running.")
            executeRequest(req, cancellation, idleRead)
          } else {
            logger.debug(s"Connection changed state since checking it was idle. Looping.")
            runRequest(req, cancellation)
          }
        case ReadActive =>
          logger.error(s"Tried to run a request already in running state.")
          F.raiseError(InProgressException)
        case Error(e) =>
          logger.debug(s"Tried to run a request in closed/error state: $e")
          F.raiseError(e)
      }
    }

  override protected def doParseContent(buffer: ByteBuffer): Option[ByteBuffer] =
    parser.doParseContent(buffer)

  override protected def contentComplete(): Boolean = parser.contentComplete()

  private def executeRequest(
      req: Request[F],
      cancellation: F[TimeoutException],
      idleRead: Option[Future[ByteBuffer]],
  ): F[Resource[F, Response[F]]] = {
    logger.debug(s"Beginning request: ${req.method} ${req.uri}")
    validateRequest(req) match {
      case Left(e) =>
        F.raiseError(e)
      case Right(req) =>
        F.defer[Resource[F, Response[F]]] {
          val initWriterSize: Int = 512
          val rr: StringWriter = new StringWriter(initWriterSize)
          val isServer: Boolean = false

          // Side Effecting Code
          encodeRequestLine(req, rr)
          Http1Stage.encodeHeaders(req.headers.headers, rr, isServer)
          if (userAgent.nonEmpty && req.headers.get[`User-Agent`].isEmpty)
            rr << userAgent.get << "\r\n"

          val mustClose: Boolean = req.headers.get[HConnection] match {
            case Some(conn) => checkCloseConnection(conn, rr)
            case None => getHttpMinor(req) == 0
          }

          val writeRequest: F[Boolean] = getChunkEncoder(req, mustClose, rr)
            .write(rr, req.body)
            .onError {
              case EOF => F.delay(shutdownWithError(EOF))
              case t =>
                F.delay(logger.error(t)("Error rendering request")) >> F.delay(shutdownWithError(t))
            }

          val idleTimeoutF: F[TimeoutException] = idleTimeoutStage match {
            case Some(stage) => F.async_[TimeoutException](stage.setTimeout)
            case None => F.never[TimeoutException]
          }

          idleTimeoutF.start.flatMap { timeoutFiber =>
            F.bracketCase(
              writeRequest.start
            )(writeFiber =>
              receiveResponse(
                mustClose,
                doesntHaveBody = req.method == Method.HEAD,
                cancellation.race(timeoutFiber.joinWithNever).map(e => Left(e.merge)),
                idleRead,
              ).map(response =>
                // We need to finish writing before we attempt to recycle the connection. We consider three scenarios:
                // - The write already finished before we got the response. This is the most common scenario. `join` completes immediately.
                // - The whole request was already transmitted and we received the response from the server, but we did not yet notice that the write is complete. This is sort of a race, it happens frequently enough when load testing. We need to wait just a moment for the `join` to finish.
                // - The server decided to reject our request before we finished sending it. The server responded (typically with an error) and closed the connection. We shouldn't wait for the `writeFiber`. This connection needs to be disposed.
                Resource.make(F.pure(response))(_ =>
                  writeFiber.join.attempt.race(closed.get >> writeFiber.cancel.start).void
                )
              )
            ) {
              case (_, Outcome.Succeeded(_)) => F.unit
              case (_, Outcome.Canceled()) => F.delay(shutdown())
              case (_, Outcome.Errored(e)) => F.delay(shutdownWithError(e))
            }
          }
        }.adaptError { case EOF =>
          new SocketException(s"HTTP connection closed: ${requestKey}")
        }
    }
  }

  private def receiveResponse(
      closeOnFinish: Boolean,
      doesntHaveBody: Boolean,
      idleTimeoutS: F[Either[Throwable, Unit]],
      idleRead: Option[Future[ByteBuffer]],
  ): F[Response[F]] =
    F.async[Response[F]] { cb =>
      F.delay {
        idleRead match {
          case Some(read) =>
            handleRead(read, cb, closeOnFinish, doesntHaveBody, "Initial Read", idleTimeoutS)
          case None =>
            handleRead(
              channelRead(),
              cb,
              closeOnFinish,
              doesntHaveBody,
              "Initial Read",
              idleTimeoutS,
            )
        }
        None
      }
    }

  // this method will get some data, and try to continue parsing using the implicit ec
  private def readAndParsePrelude(
      cb: Callback[Response[F]],
      closeOnFinish: Boolean,
      doesntHaveBody: Boolean,
      phase: String,
      idleTimeoutS: F[Either[Throwable, Unit]],
  ): Unit =
    handleRead(channelRead(), cb, closeOnFinish, doesntHaveBody, phase, idleTimeoutS)

  private def handleRead(
      read: Future[ByteBuffer],
      cb: Callback[Response[F]],
      closeOnFinish: Boolean,
      doesntHaveBody: Boolean,
      phase: String,
      idleTimeoutS: F[Either[Throwable, Unit]],
  ): Unit =
    read.onComplete {
      case Success(buff) => parsePrelude(buff, closeOnFinish, doesntHaveBody, cb, idleTimeoutS)
      case Failure(EOF) =>
        stageState.get match {
          case Error(e) => cb(Left(e))
          case _ =>
            shutdown()
            cb(Left(EOF))
        }

      case Failure(t) =>
        fatalError(t, s"Error during phase: $phase")
        cb(Left(t))
    }(executionContext)

  private def parsePrelude(
      buffer: ByteBuffer,
      closeOnFinish: Boolean,
      doesntHaveBody: Boolean,
      cb: Callback[Response[F]],
      idleTimeoutS: F[Either[Throwable, Unit]],
  ): Unit =
    try
      if (!parser.finishedResponseLine(buffer))
        readAndParsePrelude(
          cb,
          closeOnFinish,
          doesntHaveBody,
          "Response Line Parsing",
          idleTimeoutS,
        )
      else if (!parser.finishedHeaders(buffer))
        readAndParsePrelude(cb, closeOnFinish, doesntHaveBody, "Header Parsing", idleTimeoutS)
      else
        parsePreludeFinished(buffer, closeOnFinish, doesntHaveBody, cb, idleTimeoutS)
    catch {
      case t: Throwable =>
        logger.error(t)("Error during client request decode loop")
        cb(Left(t))
    }

  // it's called when headers and response line parsing are finished
  private def parsePreludeFinished(
      buffer: ByteBuffer,
      closeOnFinish: Boolean,
      doesntHaveBody: Boolean,
      cb: Callback[Response[F]],
      idleTimeoutS: F[Either[Throwable, Unit]],
  ): Unit = {
    // Get headers and determine if we need to close
    val headers: Headers = parser.getHeaders()
    val status: Status = parser.getStatus()
    val httpVersion: HttpVersion = parser.getHttpVersion()

    val (attributes, body): (Vault, EntityBody[F]) = if (doesntHaveBody) {
      // responses to HEAD requests do not have a body
      cleanUpAfterReceivingResponse(closeOnFinish, headers)
      (Vault.empty, EmptyBody)
    } else {
      // We are to the point of parsing the body and then cleaning up
      val (rawBody, _): (EntityBody[F], () => Future[ByteBuffer]) =
        collectBodyFromParser(buffer, onEofWhileReadingBody _)

      // to collect the trailers we need a cleanup helper and an effect in the attribute map
      val (trailerCleanup, attributes): (() => Unit, Vault) =
        if (parser.getHttpVersion().minor == 1 && parser.isChunked()) {
          val trailers = new AtomicReference(Headers.empty)

          val attrs = Vault.empty.insert[F[Headers]](
            Message.Keys.TrailerHeaders[F],
            F.defer {
              if (parser.contentComplete()) F.pure(trailers.get())
              else
                F.raiseError(
                  new IllegalStateException(
                    "Attempted to collect trailers before the body was complete."
                  )
                )
            },
          )

          (() => trailers.set(parser.getHeaders()), attrs)
        } else
          (() => (), Vault.empty)

      if (parser.contentComplete()) {
        trailerCleanup()
        cleanUpAfterReceivingResponse(closeOnFinish, headers)
        attributes -> rawBody
      } else
        attributes -> rawBody.onFinalizeCaseWeak {
          case ExitCase.Succeeded =>
            F.delay { trailerCleanup(); cleanUpAfterReceivingResponse(closeOnFinish, headers); }
              .evalOn(executionContext)
          case ExitCase.Errored(_) | ExitCase.Canceled =>
            F.delay {
              trailerCleanup(); cleanUpAfterReceivingResponse(closeOnFinish, headers);
              stageShutdown()
            }.evalOn(executionContext)
        }
    }

    cb(
      Right(
        Response[F](
          status = status,
          httpVersion = httpVersion,
          headers = headers,
          body = body.interruptWhen(idleTimeoutS),
          attributes = attributes,
        )
      )
    )
  }

  // It's called when an EOF is received while reading response body.
  // It's responsible for deciding if the EOF should be considered an error or an indication of the end of the body.
  private def onEofWhileReadingBody(): Either[Throwable, Option[Chunk[Byte]]] =
    stageState.get match { // if we don't have a length, EOF signals the end of the body.
      case Error(e) if e != EOF => Either.left(e)
      case _ =>
        if (parser.definedContentLength() || parser.isChunked())
          Either.left(InvalidBodyException("Received premature EOF."))
        else Either.right(None)
    }

  private def cleanUpAfterReceivingResponse(closeOnFinish: Boolean, headers: Headers): Unit =
    if (closeOnFinish || headers.get[HConnection].exists(_.hasClose)) {
      logger.debug("Message body complete. Shutting down.")
      stageShutdown()
    } else {
      logger.debug(s"Resetting $name after completing request.")
      resetRead()
    }

  // /////////////////////// Private helpers /////////////////////////

  /** Validates the request, attempting to fix it if possible,
    * returning an Exception if invalid, None otherwise
    */
  @tailrec private def validateRequest(req: Request[F]): Either[Exception, Request[F]] = {
    val minor: Int = getHttpMinor(req)

    minor match {
      // If we are HTTP/1.0, make sure HTTP/1.0 has no body or a Content-Length header
      case 0 if req.headers.get[`Content-Length`].isEmpty =>
        logger.warn(s"Request $req is HTTP/1.0 but lacks a length header. Transforming to HTTP/1.1")
        validateRequest(req.withHttpVersion(HttpVersion.`HTTP/1.1`))

      case 1 if req.uri.host.isEmpty => // this is unlikely if not impossible
        // Ensure we have a host header for HTTP/1.1
        req.headers.get[Host] match {
          case Some(host) =>
            val newAuth = req.uri.authority match {
              case Some(auth) => auth.copy(host = RegName(host.host), port = host.port)
              case None => Authority(host = RegName(host.host), port = host.port)
            }
            validateRequest(req.withUri(req.uri.copy(authority = Some(newAuth))))

          case None if req.headers.get[`Content-Length`].nonEmpty =>
            // translate to HTTP/1.0
            validateRequest(req.withHttpVersion(HttpVersion.`HTTP/1.0`))

          case None =>
            Left(new IllegalArgumentException("Host header required for HTTP/1.1 request"))
        }

      case _ if req.uri.path == Uri.Path.empty =>
        Right(req.withUri(req.uri.copy(path = Uri.Path.Root)))

      case _ if req.uri.path.renderString.exists(ForbiddenUriCharacters) =>
        Left(new IllegalArgumentException(s"Invalid URI path: ${req.uri.path}"))

      case _ =>
        Right(req) // All appears to be well
    }
  }

  private def getChunkEncoder(
      req: Request[F],
      closeHeader: Boolean,
      rr: StringWriter,
  ): Http1Writer[F] =
    getEncoder(req, rr, getHttpMinor(req), closeHeader)
}

private object Http1Connection {
  case object InProgressException extends Exception("Stage has request in progress")

  // ADT representing the state that the ClientStage can be in
  private sealed trait State
  private final case class ReadIdle(idleRead: Option[Future[ByteBuffer]]) extends State
  private case object ReadActive extends State
  private final case class Error(exc: Throwable) extends State

  private def getHttpMinor[F[_]](req: Request[F]): Int = req.httpVersion.minor

  private def encodeRequestLine[F[_]](req: Request[F], writer: Writer): writer.type = {
    val uri = req.uri
    writer << req.method << ' ' << uri.toOriginForm << ' ' << req.httpVersion << "\r\n"
    if (
      getHttpMinor(req) == 1 &&
      req.headers.get[Host].isEmpty
    ) { // need to add the host header for HTTP/1.1
      uri.host match {
        case Some(host) =>
          writer << "Host: " << host.value
          if (uri.port.isDefined) writer << ':' << uri.port.get
          writer << "\r\n"

        case None =>
          // TODO: do we want to do this by exception?
          throw new IllegalArgumentException("Request URI must have a host.")
      }
      writer
    } else writer
  }

  private val ForbiddenUriCharacters = CharPredicate(0x0.toChar, '\r', '\n')

}
