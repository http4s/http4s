/*
 * Copyright 2019 http4s.org
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

package org.http4s.ember.core

import cats._
import cats.effect.{MonadThrow => _, _}
import cats.effect.concurrent.{Deferred, Ref}
import cats.syntax.all._
import fs2._
import org.http4s._
import scala.annotation.switch
import scala.collection.mutable

private[ember] object Parser {

  final case class Message(bytes: Array[Byte], rest: Array[Byte])

  object Message {

    private val cr: Byte = '\r'.toByte
    private val lf: Byte = '\n'.toByte
    private val DoubleCrlf: Seq[Byte] = (cr, lf, cr, lf)

    def parseMessage[F[_]](buffer: Array[Byte], read: F[Option[Chunk[Byte]]], maxHeaderLength: Int)(implicit F: MonadThrow[F]): F[Message] = {
      val endIndex = buffer.take(maxHeaderLength).indexOfSlice(DoubleCrlf)
      if (endIndex == -1 && buffer.length > maxHeaderLength) {
        F.raiseError(new Throwable("HTTP Message Too Long"))
      } else if (endIndex == -1) {
        read.flatMap {
          case Some(chunk) =>
            val nextBuffer = combineArrays(buffer, chunk.toArray)
            parseMessage(nextBuffer, read, maxHeaderLength)
          case None => F.raiseError(new Throwable("Reached End Of Stream"))
        }
      } else {
        val (bytes, rest) = buffer.splitAt(endIndex)
        Message(bytes, rest).pure[F]
      }
    }

  }

  final case class HeaderP(headers: Headers, chunked: Boolean, contentLength: Option[Long], rest: Array[Byte])

  object HeaderP {

    def parseHeaders[F[_]](
        head: Array[Byte],
        read: F[Option[Chunk[Byte]]],
        maxHeaderLength: Int,
        acc: Option[ParseHeadersIncomplete])(implicit
        F: MonadThrow[F]): F[HeaderP] = {
      // TODO: improve this
      val nextChunk = if (head.nonEmpty) F.pure(Some(Chunk.bytes(head))) else read
      nextChunk.flatMap {
        case Some(chunk) =>
          val nextArr: Array[Byte] = acc match {
            case None => chunk.toArray
            case Some(last) => combineArrays(last.bv, chunk.toArray)
          }
          val result = acc match {
            case None => headersInSection(nextArr)
            case Some(
                  ParseHeadersIncomplete(
                    _,
                    accHeaders,
                    idx,
                    state,
                    name,
                    start,
                    chunked,
                    contentLength)) =>
              headersInSection(nextArr, idx, state, accHeaders, chunked, contentLength, name, start)
          }
          result match {
            case ParseHeadersCompleted(headers, rest, chunked, length) =>
              F.pure(HeaderP(headers, chunked, length, rest))
            case p @ ParseHeadersError(_) => F.raiseError(p)
            case p @ ParseHeadersIncomplete(_, _, _, _, _, _, _, _) =>
              if (nextArr.size <= maxHeaderLength)
                parseHeaders(Array.emptyByteArray, read, maxHeaderLength, p.some)
              else
                F.raiseError(
                  ParseHeadersError(
                    new Throwable(
                      s"Parse Headers Exceeded Max Content-Length current size: ${nextArr.size}, only allow ${maxHeaderLength}")
                  )
                )
          }
        case None =>
          F.raiseError(
            ParseHeadersError(new Throwable("Reached End of Stream Looking for Headers")))
      }
    }

    private val colon: Byte = ':'.toByte
    private val cr: Byte = '\r'.toByte
    private val lf: Byte = '\n'.toByte
    private val space: Byte = ' '.toByte
    private val contentLengthS = "Content-Length"
    private val transferEncodingS = "Transfer-Encoding"
    private val chunkedS = "chunked"

    sealed trait ParseHeaderResult
    final case class ParseHeadersError(cause: Throwable)
        extends Throwable(
          s"Encountered Error Attempting to Parse Headers - ${cause.getMessage}",
          cause)
        with ParseHeaderResult
    final case class ParseHeadersCompleted(
        headers: Headers,
        rest: Array[Byte],
        chunked: Boolean,
        length: Option[Long])
        extends ParseHeaderResult
    final case class ParseHeadersIncomplete(
        bv: Array[Byte],
        accHeaders: List[Header],
        idx: Int,
        state: Byte,
        name: Option[String],
        start: Int,
        chunked: Boolean,
        contentLength: Option[Long])
        extends ParseHeaderResult

    def headersInSection(
        bv: Array[Byte],
        initIndex: Int = 0,
        initState: Byte = 0, //HeaderNameOrPostCRLF,
        initHeaders: List[Header] = List.empty,
        initChunked: Boolean = false,
        initContentLength: Option[Long] = None,
        initName: Option[String] = None,
        initStart: Int = 0
    ): ParseHeaderResult = {
      import scala.collection.mutable.ListBuffer
      var idx = initIndex
      var state = initState
      var throwable: Throwable = null
      var complete = false
      var chunked: Boolean = initChunked
      var contentLength: Option[Long] = initContentLength

      val headers = ListBuffer(initHeaders: _*)
      var name: String = initName.orNull
      var start = initStart

      while (!complete && idx < bv.size) {
        (state: @switch) match {
          case 0 => // HeaderNameOrPostCRLF
            val current = bv(idx)
            // if current index is colon our name is complete
            if (current == colon) {
              state = 1 // set state to check for header value
              name = new String(bv, start, idx - start) // extract name string
              start = idx + 1 // advance past colon for next start

              // TODO: This if clause may not be necessary since the header value parser trims
              if ((bv.size > idx + 1 && bv(idx + 1) == space)) {
                start += 1 // if colon is followed by space advance again
                idx += 1 // double advance index here to skip the space
              }
              // double CRLF condition - Termination of headers
            } else if (current == lf && (idx > 0 && bv(idx - 1) == cr)) {
              complete = true // completed terminate loop
            }
          case 1 => // HeaderValue
            val current = bv(idx)
            // If crlf is next we have completed the header value
            if (current == lf && (idx > 0 && bv(idx - 1) == cr)) {
              // extract header value, trim leading and trailing whitespace
              val hValue = new String(bv, start, idx - start - 1).trim

              val hName = name // copy var to val
              name = null // set name back to null
              val newHeader = Header(hName, hValue) // create header
              if (hName.equalsIgnoreCase(contentLengthS)) { // Check if this is content-length.
                try contentLength = hValue.toLong.some
                catch {
                  case scala.util.control.NonFatal(e) =>
                    throwable = e
                    complete = true
                }
              } else if (hName
                  .equalsIgnoreCase(transferEncodingS)) { // Check if this is Transfer-encoding
                chunked = hValue.contains(chunkedS)
              }
              start = idx + 1 // Next Start is after the CRLF
              headers += newHeader // Add Header
              state = 0 // Go back to Looking for HeaderName or Termination
            }
        }
        idx += 1 // Single Advance Every Iteration
      }

      if (throwable != null) ParseHeadersError(throwable)
      else if (complete)
        ParseHeadersCompleted(Headers(headers.toList), bv.drop(idx), chunked, contentLength)
      else
        ParseHeadersIncomplete(
          bv,
          headers.toList,
          idx,
          state,
          Option(name),
          start,
          chunked,
          contentLength)
    }
  }

  object Request {

    final case class ReqPrelude(method: Method, uri: Uri, version: HttpVersion, rest: Array[Byte], nextIndex: Int)

    object ReqPrelude {

      private val space = ' '.toByte
      private val cr: Byte = '\r'.toByte
      private val lf: Byte = '\n'.toByte

      // Method SP URI SP HttpVersion CRLF - REST
      def parsePrelude[F[_]](message: Array[Byte])(implicit F: MonadThrow[F]): F[ReqPrelude] = {
        var idx = 0
        var state: Byte = 0
        var complete = false

        var throwable: Throwable = null
        var method: Method = initMethod.orNull
        var uri: Uri = initUri.orNull
        var httpVersion: HttpVersion = initHttpVersion.orNull

        var start = 0
        while (!complete && idx < bv.size) {
          (state: @switch) match {
            case 0 =>
              if (value == space) {
                Method.fromString(new String(bv, start, idx - start)) match {
                  case Left(e) =>
                    throwable = e
                    complete = true
                  case Right(m) =>
                    method = m
                }
                start = idx + 1
                state = 1
              }
            case 1 =>
              val value = bv(idx)
              if (value == space) {
                Uri.fromString(new String(bv, start, idx - start)) match {
                  case Left(e) =>
                    throwable = e
                    complete = true
                  case Right(u) =>
                    uri = u
                }
                start = idx + 1
                state = 2
              }
            case 2 =>
              val value = bv(idx)
              if (value == lf && (idx > 0 && bv(idx - 1) == cr)) {
                HttpVersion.fromString(new String(bv, start, idx - start - 1)) match {
                  case Left(e) =>
                    throwable = e
                    complete = true
                  case Right(h) =>
                    httpVersion = h
                }
                complete = true
              }
          }
          idx += 1
        }

        if (throwable != null)
          F.raiseError(ParsePreludeError(
            throwable.getMessage(),
            Option(throwable),
            Option(method),
            Option(uri),
            Option(httpVersion)
          ))
        else if (method != null || uri != null || httpVersion != null)
          F.raiseError(ParsePreludeError(
            new Throwable("Failed to parse HTTP request prelude"),
            Option(throwable),
            Option(method),
            Option(uri),
            Option(httpVersion)
          ))
        else
          ReqPrelude(m, u, h, rest, idx).pure[F]
      }
      
      final case class ParsePreludeError(
          message: String,
          caused: Option[Throwable],
          method: Option[Method],
          uri: Option[Uri],
          httpVersion: Option[HttpVersion]
      ) extends Exception(
            s"Parse Prelude Error Encountered - Message: $message - Partially Decoded: $method $uri $httpVersion",
            caused.orNull
          )
    }

    def parser[F[_]](maxHeaderLength: Int)(
        head: Array[Byte],
        read: F[Option[Chunk[Byte]]]
    )(implicit F: Concurrent[F]): F[(Request[F], F[Option[Array[Byte]]])] =
      ReqPrelude
        .parsePrelude[F](head, read, maxHeaderLength, None)
        .flatMap { reqPrelude =>
          HeaderP.parseHeaders(reqPrelude.rest, read, maxHeaderLength, None).flatMap { headerP =>
            val baseReq: org.http4s.Request[F] = org.http4s.Request[F](
              method = reqPrelude.method,
              uri = reqPrelude.uri,
              httpVersion = reqPrelude.version,
              headers = headerP.headers
            )

            if (headerP.chunked) {
              Ref.of[F, Option[Array[Byte]]](None).product(Deferred[F, Headers]).map {
                case (rest, trailers) =>
                  (
                    baseReq
                      .withAttribute(Message.Keys.TrailerHeaders[F], trailers.get)
                      .withBodyStream(
                        ChunkedEncoding.decode(headerP.rest, read, maxHeaderLength, trailers, rest)),
                    rest.get)
              }
            } else {
              Body.parseFixedBody(headerP.contentLength.getOrElse(0L), headerP.rest, read).map {
                case (bodyStream, drain) =>
                  (baseReq.withBodyStream(bodyStream), drain)
              }
            }
          }
        }
  }

  object Response {

    def parser[F[_]: Concurrent](maxHeaderLength: Int)(
        head: Array[Byte],
        read: F[Option[Chunk[Byte]]]
    ): F[(Response[F], F[Option[Array[Byte]]])] =
      RespPrelude
        .parsePrelude(head, read, maxHeaderLength, None)
        .flatMap { respPrelude =>
          HeaderP.parseHeaders(respPrelude.rest, read, maxHeaderLength, None).flatMap { headerP =>
            val baseResp = org.http4s.Response[F](
              httpVersion = respPrelude.version,
              status = respPrelude.status,
              headers = headerP.headers
            )

            if (headerP.chunked) {
              Ref.of[F, Option[Array[Byte]]](None).product(Deferred[F, Headers]).map {
                case (rest, trailers) =>
                  (
                    baseResp
                      .withAttribute(Message.Keys.TrailerHeaders[F], trailers.get)
                      .withBodyStream(
                        ChunkedEncoding.decode(headerP.rest, read, maxHeaderLength, trailers, rest)),
                    rest.get)
              }
            } else {
              Body.parseFixedBody(headerP.contentLength.getOrElse(0L), headerP.rest, read).map {
                case (bodyStream, drain) =>
                  (baseResp.withBodyStream(bodyStream), drain)
              }
            }
          }
        }

    object RespPrelude {

      final case class RespPrelude(version: HttpVersion, status: Status, rest: Array[Byte])

      val emptyStreamError = RespPreludeError("Cannot Parse Empty Stream", None)

      def parsePrelude[F[_]](
          head: Array[Byte],
          read: F[Option[Chunk[Byte]]],
          maxHeaderLength: Int,
          acc: Option[Array[Byte]] = None)(implicit
          F: MonadThrow[F]): F[RespPrelude] = {
        val pull = if (head.nonEmpty) F.pure(Some(Chunk.bytes(head))) else read

        pull.flatMap {
          case Some(chunk) =>
            val next: Array[Byte] = acc match {
              case None => chunk.toArray
              case Some(remains) => combineArrays(remains, chunk.toArray)
            }
            preludeInSection(next) match {
              case RespPreludeComplete(httpVersion, status, rest) =>
                RespPrelude(httpVersion, status, rest).pure[F]
              case t @ RespPreludeError(_, _) => F.raiseError(t)
              case RespPreludeIncomplete =>
                if (next.size <= maxHeaderLength)
                  parsePrelude(Array.emptyByteArray, read, maxHeaderLength, next.some)
                else
                  F.raiseError(
                    RespPreludeError(
                      "Reached Max Header Length Looking for Response Prelude",
                      None))
            }
          case None =>
            acc match {
              case None => F.raiseError(emptyStreamError)
              case Some(incomplete) if incomplete.isEmpty => F.raiseError(emptyStreamError)
              case Some(_) =>
                F.raiseError(
                  RespPreludeError(
                    "Unexpectedly Reached Ended of Stream Looking for Response Prelude",
                    None)
                )
            }

        }
      }

      private val space = ' '.toByte
      private val cr: Byte = '\r'.toByte
      private val lf: Byte = '\n'.toByte

      sealed trait RespPreludeResult
      case class RespPreludeComplete(httpVersion: HttpVersion, status: Status, rest: Array[Byte])
          extends RespPreludeResult
      case object RespPreludeIncomplete extends RespPreludeResult
      case class RespPreludeError(message: String, cause: Option[Throwable])
          extends Throwable(
            s"Received Error while parsing prelude - Message: $message - ${cause.map(_.getMessage)}",
            cause.orNull)
          with RespPreludeResult

      // HTTP/1.1 200 OK
      // Status-Line = HTTP-Version SP Status-Code SP Reason-Phrase CRLF
      def preludeInSection(bv: Array[Byte]): RespPreludeResult = {
        var complete = false
        var idx = 0
        var throwable: Throwable = null
        var httpVersion: HttpVersion = null

        var codeS: String = null
        // val reason: String = null
        var status: Status = null
        var start = 0
        var state = 0 // 0 Is for HttpVersion, 1 for Status Code, 2 For Reason Phrase

        while (!complete && idx < bv.size) {
          val value = bv(idx)
          (state: @switch) match {
            case 0 =>
              if (value == space) {
                val s = new String(bv, start, idx - start)
                HttpVersion.fromString(s) match {
                  case Left(e) =>
                    throwable = e
                    complete = true
                  case Right(h) =>
                    httpVersion = h
                }
                start = idx + 1
                state = 1
              }
            case 1 =>
              if (value == space) {
                codeS = new String(bv, start, idx - start)
                state = 2
                start = idx + 1
              }
            case 2 =>
              if (value == lf && (idx > 0 && bv(idx - 1) == cr)) {
                val reason = new String(bv, start, idx - start - 1)
                try {
                  val codeInt = codeS.toInt
                  Status.fromIntAndReason(codeInt, reason) match {
                    case Left(e) =>
                      throw e
                    case Right(s) =>
                      status = s
                      complete = true
                  }
                } catch {
                  case scala.util.control.NonFatal(e) =>
                    throwable = e
                    complete = true
                }
              }
          }
          idx += 1
        }

        if (throwable != null) RespPreludeError("Encounterd Error parsing", Option(throwable))
        if (httpVersion != null && status != null)
          RespPreludeComplete(httpVersion, status, bv.drop(idx))
        else RespPreludeIncomplete
      }
    }
  }

  object Body {
    def parseFixedBody[F[_]: Concurrent](
        contentLength: Long,
        head: Array[Byte],
        read: F[Option[Chunk[Byte]]]): F[(EntityBody[F], F[Option[Array[Byte]]])] =
      if (contentLength > 0) {
        if (head.length >= contentLength) {
          val (body, rest) = head.splitAt(contentLength.toInt)
          (Stream.chunk(Chunk.bytes(body)).covary[F], (Some(rest): Option[Array[Byte]]).pure[F])
            .pure[F]
        } else {
          val unread = contentLength - head.length
          Ref.of[F, Either[Long, Array[Byte]]](Left(unread)).map { state =>
            val bodyStream = Stream.eval(state.get).flatMap {
              case Right(_) =>
                Stream.raiseError(new Throwable("Body has already been completely read"))
              case Left(remaining) =>
                readStream(read).chunks
                  .evalMapAccumulate(remaining) { case (r, chunk) =>
                    if (chunk.size >= r) {
                      val (rest, after) = chunk.splitAt(r.toInt)
                      state.set(Right(after.toArray)).as((0L, rest))
                    } else {
                      val r2 = r - chunk.size
                      state.set(Left(r2)).as((r2, chunk))
                    }
                  }
                  .takeThrough(_._1 > 0)
                  .flatMap(t => Stream.chunk(t._2))
            }

            // If the remaining bytes for the body have not yet been read, close the connection.
            // followup: Check if there are bytes immediately available without blocking
            val drain: F[Option[Array[Byte]]] = state.get.map(_.toOption)

            (Stream.chunk(Chunk.bytes(head)) ++ bodyStream, drain)
          }
        }
      } else {
        (EmptyBody.covary[F], (Some(head): Option[Array[Byte]]).pure[F]).pure[F]
      }

    private def readStream[F[_]](read: F[Option[Chunk[Byte]]]): Stream[F, Byte] =
      Stream.eval(read).flatMap {
        case Some(bytes) =>
          Stream.chunk(bytes) ++ readStream(read)
        case None => Stream.empty
      }
  }

  private def combineArrays[A: scala.reflect.ClassTag](a1: Array[A], a2: Array[A]): Array[A] = {
    val buff = mutable.ArrayBuffer[A]()
    buff.++=(a1)
    buff.++=(a2)
    buff.toArray
  }
}
