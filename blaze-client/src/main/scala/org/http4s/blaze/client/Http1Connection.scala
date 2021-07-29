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

import cats.effect.kernel.{Async, Outcome, Resource}
import cats.effect.std.Dispatcher
import cats.effect.implicits._
import cats.syntax.all._
import fs2._

import java.nio.ByteBuffer
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import org.http4s.Uri.{Authority, RegName}
import org.http4s.blaze.pipeline.Command.EOF
import org.http4s.blazecore.{Http1Stage, IdleTimeoutStage}
import org.http4s.blazecore.util.Http1Writer
import org.http4s.client.RequestKey
import org.http4s.headers.{Connection, Host, `Content-Length`, `User-Agent`}
import org.http4s.util.{StringWriter, Writer}
import org.typelevel.vault._
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

private final class Http1Connection[F[_]](
    val requestKey: RequestKey,
    protected override val executionContext: ExecutionContext,
    maxResponseLineSize: Int,
    maxHeaderLength: Int,
    maxChunkSize: Int,
    override val chunkBufferMaxSize: Int,
    parserMode: ParserMode,
    userAgent: Option[`User-Agent`],
    idleTimeoutStage: Option[IdleTimeoutStage[ByteBuffer]],
    override val dispatcher: Dispatcher[F]
)(implicit protected val F: Async[F])
    extends Http1Stage[F]
    with BlazeConnection[F] {
  import Http1Connection._
  import Resource.ExitCase

  override def name: String = getClass.getName
  private val parser =
    new BlazeHttp1ClientParser(maxResponseLineSize, maxHeaderLength, maxChunkSize, parserMode)

  private val stageState = new AtomicReference[State](Idle(None))

  override def isClosed: Boolean =
    stageState.get match {
      case Error(_) => true
      case _ => false
    }

  override def isRecyclable: Boolean =
    stageState.get match {
      case Idle(_) => true
      case _ => false
    }

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
        else closePipeline(Some(t))

      case Error(_) => // NOOP: already shutdown

      case x =>
        if (!stageState.compareAndSet(x, Error(t))) shutdownWithError(t)
        else {
          val cmd = t match {
            case EOF => None
            case _ => Some(t)
          }
          closePipeline(cmd)
          super.stageShutdown()
        }
    }

  @tailrec
  def resetRead(): Unit = {
    val state = stageState.get()
    val nextState = state match {
      case ReadWrite => Some(Write)
      case Read =>
        // idleTimeout is activated when entering ReadWrite state, remains active throughout Read and Write and is deactivated when entering the Idle state
        idleTimeoutStage.foreach(_.cancelTimeout())
        Some(Idle(Some(startIdleRead())))
      case _ => None
    }

    nextState match {
      case Some(n) => if (stageState.compareAndSet(state, n)) parser.reset() else resetRead()
      case None => ()
    }
  }

  @tailrec
  def resetWrite(): Unit = {
    val state = stageState.get()
    val nextState = state match {
      case ReadWrite => Some(Read)
      case Write =>
        // idleTimeout is activated when entering ReadWrite state, remains active throughout Read and Write and is deactivated when entering the Idle state
        idleTimeoutStage.foreach(_.cancelTimeout())
        Some(Idle(Some(startIdleRead())))
      case _ => None
    }

    nextState match {
      case Some(n) => if (stageState.compareAndSet(state, n)) () else resetWrite()
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

  def runRequest(req: Request[F]): F[Resource[F, Response[F]]] =
    F.defer[Resource[F, Response[F]]] {
      stageState.get match {
        case i @ Idle(idleRead) =>
          if (stageState.compareAndSet(i, ReadWrite)) {
            logger.debug(s"Connection was idle. Running.")
            executeRequest(req, idleRead)
          } else {
            logger.debug(s"Connection changed state since checking it was idle. Looping.")
            runRequest(req)
          }
        case ReadWrite | Read | Write =>
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
      idleRead: Option[Future[ByteBuffer]]): F[Resource[F, Response[F]]] = {
    logger.debug(s"Beginning request: ${req.method} ${req.uri}")
    validateRequest(req) match {
      case Left(e) =>
        F.raiseError(e)
      case Right(req) =>
        F.defer {
          val initWriterSize: Int = 512
          val rr: StringWriter = new StringWriter(initWriterSize)
          val isServer: Boolean = false

          // Side Effecting Code
          encodeRequestLine(req, rr)
          Http1Stage.encodeHeaders(req.headers.headers, rr, isServer)
          if (userAgent.nonEmpty && req.headers.get[`User-Agent`].isEmpty)
            rr << userAgent.get << "\r\n"

          val mustClose: Boolean = req.headers.get[Connection] match {
            case Some(conn) => checkCloseConnection(conn, rr)
            case None => getHttpMinor(req) == 0
          }

          val idleTimeoutF = idleTimeoutStage match {
            case Some(stage) => F.async_[TimeoutException](stage.setTimeout)
            case None => F.never[TimeoutException]
          }

          idleTimeoutF.start.flatMap { timeoutFiber =>
            val idleTimeoutS = timeoutFiber.joinWithNever.attempt.map {
              case Right(t) => Left(t): Either[Throwable, Unit]
              case Left(t) => Left(t): Either[Throwable, Unit]
            }

            val writeRequest: F[Boolean] = getChunkEncoder(req, mustClose, rr)
              .write(rr, req.body)
              .guarantee(F.delay(resetWrite()))
              .onError {
                case EOF => F.unit
                case t => F.delay(logger.error(t)("Error rendering request"))
              }

            val response: F[Resource[F, Response[F]]] =
              F.bracketCase(
                writeRequest.start
              )(writeFiber =>
                receiveResponse(
                  mustClose,
                  doesntHaveBody = req.method == Method.HEAD,
                  idleTimeoutS,
                  idleRead
                  // We need to wait for the write to complete so that by the time we attempt to recycle the connection it is fully idle.
                ).map(response =>
                  Resource.make(F.pure(writeFiber))(_.join.attempt.void).as(response))) {
                case (_, Outcome.Succeeded(_)) => F.unit
                case (writeFiber, Outcome.Canceled() | Outcome.Errored(_)) => writeFiber.cancel
              }

            F.race(response, timeoutFiber.joinWithNever)
              .flatMap {
                case Left(r) =>
                  F.pure(r)
                case Right(t) =>
                  F.raiseError(t)
              }
          }
        }
    }
  }

  private def receiveResponse(
      closeOnFinish: Boolean,
      doesntHaveBody: Boolean,
      idleTimeoutS: F[Either[Throwable, Unit]],
      idleRead: Option[Future[ByteBuffer]]): F[Response[F]] =
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
              idleTimeoutS)
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
      idleTimeoutS: F[Either[Throwable, Unit]]): Unit =
    handleRead(channelRead(), cb, closeOnFinish, doesntHaveBody, phase, idleTimeoutS)

  private def handleRead(
      read: Future[ByteBuffer],
      cb: Callback[Response[F]],
      closeOnFinish: Boolean,
      doesntHaveBody: Boolean,
      phase: String,
      idleTimeoutS: F[Either[Throwable, Unit]]): Unit =
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
      idleTimeoutS: F[Either[Throwable, Unit]]): Unit =
    try if (!parser.finishedResponseLine(buffer))
      readAndParsePrelude(cb, closeOnFinish, doesntHaveBody, "Response Line Parsing", idleTimeoutS)
    else if (!parser.finishedHeaders(buffer))
      readAndParsePrelude(cb, closeOnFinish, doesntHaveBody, "Header Parsing", idleTimeoutS)
    else {
      // Get headers and determine if we need to close
      val headers: Headers = parser.getHeaders()
      val status: Status = parser.getStatus()
      val httpVersion: HttpVersion = parser.getHttpVersion()

      // we are now to the body
      def terminationCondition(): Either[Throwable, Option[Chunk[Byte]]] =
        stageState.get match { // if we don't have a length, EOF signals the end of the body.
          case Error(e) if e != EOF => Either.left(e)
          case _ =>
            if (parser.definedContentLength() || parser.isChunked())
              Either.left(InvalidBodyException("Received premature EOF."))
            else Either.right(None)
        }

      def cleanup(): Unit =
        if (closeOnFinish || headers.get[Connection].exists(_.hasClose)) {
          logger.debug("Message body complete. Shutting down.")
          stageShutdown()
        } else {
          logger.debug(s"Resetting $name after completing request.")
          resetRead()
        }

      val (attributes, body): (Vault, EntityBody[F]) = if (doesntHaveBody) {
        // responses to HEAD requests do not have a body
        cleanup()
        (Vault.empty, EmptyBody)
      } else {
        // We are to the point of parsing the body and then cleaning up
        val (rawBody, _): (EntityBody[F], () => Future[ByteBuffer]) =
          collectBodyFromParser(buffer, terminationCondition _)

        // to collect the trailers we need a cleanup helper and an effect in the attribute map
        val (trailerCleanup, attributes): (() => Unit, Vault) = {
          if (parser.getHttpVersion().minor == 1 && parser.isChunked()) {
            val trailers = new AtomicReference(Headers.empty)

            val attrs = Vault.empty.insert[F[Headers]](
              Message.Keys.TrailerHeaders[F],
              F.defer {
                if (parser.contentComplete()) F.pure(trailers.get())
                else
                  F.raiseError(
                    new IllegalStateException(
                      "Attempted to collect trailers before the body was complete."))
              }
            )

            (() => trailers.set(parser.getHeaders()), attrs)
          } else
            (
              { () =>
                ()
              },
              Vault.empty)
        }

        if (parser.contentComplete()) {
          trailerCleanup()
          cleanup()
          attributes -> rawBody
        } else
          attributes -> rawBody.onFinalizeCaseWeak {
            case ExitCase.Succeeded =>
              F.delay { trailerCleanup(); cleanup(); }.evalOn(executionContext)
            case ExitCase.Errored(_) | ExitCase.Canceled =>
              F.delay {
                trailerCleanup(); cleanup(); stageShutdown()
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
            attributes = attributes)
        ))
    } catch {
      case t: Throwable =>
        logger.error(t)("Error during client request decode loop")
        cb(Left(t))
    }

  ///////////////////////// Private helpers /////////////////////////

  /** Validates the request, attempting to fix it if possible,
    * returning an Exception if invalid, None otherwise
    */
  @tailrec private def validateRequest(req: Request[F]): Either[Exception, Request[F]] = {
    val minor: Int = getHttpMinor(req)

    // If we are HTTP/1.0, make sure HTTP/1.0 has no body or a Content-Length header
    if (minor == 0 && req.headers.get[`Content-Length`].isEmpty) {
      logger.warn(s"Request $req is HTTP/1.0 but lacks a length header. Transforming to HTTP/1.1")
      validateRequest(req.withHttpVersion(HttpVersion.`HTTP/1.1`))
    }
    // Ensure we have a host header for HTTP/1.1
    else if (minor == 1 && req.uri.host.isEmpty) // this is unlikely if not impossible
      if (req.headers.get[Host].isDefined) {
        val host = req.headers.get[Host].get // TODO gross
        val newAuth = req.uri.authority match {
          case Some(auth) => auth.copy(host = RegName(host.host), port = host.port)
          case None => Authority(host = RegName(host.host), port = host.port)
        }
        validateRequest(req.withUri(req.uri.copy(authority = Some(newAuth))))
      } else if (req.headers.get[`Content-Length`].nonEmpty) // translate to HTTP/1.0
        validateRequest(req.withHttpVersion(HttpVersion.`HTTP/1.0`))
      else
        Left(new IllegalArgumentException("Host header required for HTTP/1.1 request"))
    else if (req.uri.path == Uri.Path.empty) Right(req.withUri(req.uri.copy(path = Uri.Path.Root)))
    else Right(req) // All appears to be well
  }

  private def getChunkEncoder(
      req: Request[F],
      closeHeader: Boolean,
      rr: StringWriter): Http1Writer[F] =
    getEncoder(req, rr, getHttpMinor(req), closeHeader)
}

private object Http1Connection {
  case object InProgressException extends Exception("Stage has request in progress")

  // ADT representing the state that the ClientStage can be in
  private sealed trait State
  private final case class Idle(idleRead: Option[Future[ByteBuffer]]) extends State
  private case object ReadWrite extends State
  private case object Read extends State
  private case object Write extends State
  private final case class Error(exc: Throwable) extends State

  private def getHttpMinor[F[_]](req: Request[F]): Int = req.httpVersion.minor

  private def encodeRequestLine[F[_]](req: Request[F], writer: Writer): writer.type = {
    val uri = req.uri
    writer << req.method << ' ' << uri.toOriginForm << ' ' << req.httpVersion << "\r\n"
    if (getHttpMinor(req) == 1 &&
      req.headers.get[Host].isEmpty) { // need to add the host header for HTTP/1.1
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
}
