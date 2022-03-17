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

  final case class MessageP(bytes: Array[Byte], rest: Array[Byte])

  object MessageP {

    private[this] val doubleCrlf: Seq[Byte] = Seq(cr, lf, cr, lf)

    def parseMessage[F[_]](buffer: Array[Byte], read: Read[F], maxHeaderSize: Int)(implicit
        F: MonadThrow[F]): F[MessageP] = {
      val endIndex = buffer.take(maxHeaderSize).indexOfSlice(doubleCrlf)
      if (endIndex == -1 && buffer.length > maxHeaderSize) {
        F.raiseError(MessageTooLongError(maxHeaderSize))
      } else if (endIndex == -1) {
        read.flatMap {
          case Some(chunk) =>
            val nextBuffer = combineArrays(buffer, chunk.toArray)
            parseMessage(nextBuffer, read, maxHeaderSize)
          case None if buffer.length > 0 => F.raiseError(EndOfStreamError())
          case _ => F.raiseError(EmptyStreamError())
        }
      } else {
        val (bytes, rest) = buffer.splitAt(endIndex + 4)
        MessageP(bytes, rest).pure[F]
      }
    }

    final case class MessageTooLongError(maxHeaderSize: Int)
        extends Exception(s"HTTP Header Section Exceeds Max Size: $maxHeaderSize Bytes")

    final case class EndOfStreamError() extends Exception("Reached End Of Stream")

  }

  final case class HeaderP(headers: Headers, chunked: Boolean, contentLength: Option[Long])

  object HeaderP {

    private[this] val colon: Byte = ':'.toByte
    private[this] val contentLengthS = "Content-Length"
    private[this] val transferEncodingS = "Transfer-Encoding"
    private[this] val chunkedS = "chunked"

    def parseHeaders[F[_]](message: Array[Byte], initIndex: Int)(implicit
        F: MonadThrow[F]): F[HeaderP] = {
      import scala.collection.mutable.ListBuffer
      var idx = initIndex
      var state = 0
      var throwable: Throwable = null
      var complete = false
      var chunked: Boolean = false
      var contentLength: Option[Long] = None

      val headers: ListBuffer[Header] = ListBuffer()
      var name: String = null
      var start = initIndex

      while (!complete && idx < message.size) {
        (state: @switch) match {
          case 0 => // HeaderNameOrPostCRLF
            val current = message(idx)
            // if current index is colon our name is complete
            if (current == colon) {
              state = 1 // set state to check for header value
              name = new String(message, start, idx - start) // extract name string
              start = idx + 1 // advance past colon for next start

              // TODO: This if clause may not be necessary since the header value parser trims
              if ((message.size > idx + 1 && message(idx + 1) == space)) {
                start += 1 // if colon is followed by space advance again
                idx += 1 // double advance index here to skip the space
              }
              // double CRLF condition - Termination of headers
            } else if (current == lf && (idx > 0 && message(idx - 1) == cr)) {
              complete = true // completed terminate loop
            }
          case 1 => // HeaderValue
            val current = message(idx)
            // If crlf is next we have completed the header value
            if (current == lf && (idx > 0 && message(idx - 1) == cr)) {
              // extract header value, trim leading and trailing whitespace
              val hValue = new String(message, start, idx - start - 1).trim

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

      if (throwable != null) {
        F.raiseError(ParseHeadersError(throwable))
      } else if (!complete) {
        F.raiseError(IncompleteHttpMessage(Headers(headers.toList)))
      } else {
        HeaderP(Headers(headers.toList), chunked, contentLength).pure[F]
      }
    }

    final case class ParseHeadersError(cause: Throwable)
        extends Exception(
          s"Encountered Error Attempting to Parse Headers - ${cause.getMessage}",
          cause)

    final case class IncompleteHttpMessage(headers: Headers)
        extends Exception("Tried To Parse An Incomplete HTTP Message")
  }

  object Request {

    final case class ReqPrelude(method: Method, uri: Uri, version: HttpVersion, nextIndex: Int)

    object ReqPrelude {

      // Method SP URI SP HttpVersion CRLF - REST
      def parsePrelude[F[_]](message: Array[Byte])(implicit F: MonadThrow[F]): F[ReqPrelude] = {
        var idx = 0
        var state: Byte = 0
        var complete = false

        var throwable: Throwable = null
        var method: Method = null
        var uri: Uri = null
        var httpVersion: HttpVersion = null

        var start = 0
        while (!complete && idx < message.size) {
          val value = message(idx)
          (state: @switch) match {
            case 0 =>
              if (value == space) {
                Method.fromString(new String(message, start, idx - start)) match {
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
              if (value == space) {
                Uri.fromString(new String(message, start, idx - start)) match {
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
              if (value == lf && (idx > 0 && message(idx - 1) == cr)) {
                HttpVersion.fromString(new String(message, start, idx - start - 1)) match {
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
          F.raiseError(
            ParsePreludeError(
              throwable.getMessage(),
              Option(throwable),
              Option(method),
              Option(uri),
              Option(httpVersion)
            ))
        else if (method == null || uri == null || httpVersion == null)
          F.raiseError(
            ParsePreludeError(
              "Failed to parse HTTP request prelude",
              Option(throwable),
              Option(method),
              Option(uri),
              Option(httpVersion)
            ))
        else
          ReqPrelude(method, uri, httpVersion, idx).pure[F]
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

    def parser[F[_]](maxHeaderSize: Int)(
        buffer: Array[Byte],
        read: Read[F]
    )(implicit F: Concurrent[F]): F[(Request[F], Drain[F])] =
      for {
        message <- MessageP.parseMessage(buffer, read, maxHeaderSize)
        prelude <- ReqPrelude.parsePrelude(message.bytes)
        headerP <- HeaderP.parseHeaders(message.bytes, prelude.nextIndex)

        baseReq = org.http4s.Request[F](
          method = prelude.method,
          uri = prelude.uri,
          httpVersion = prelude.version,
          headers = headerP.headers
        )

        request <-
          if (headerP.chunked) {
            Ref.of[F, Option[Array[Byte]]](None).product(Deferred[F, Headers]).map {
              case (rest, trailers) =>
                (
                  baseReq
                    .withAttribute(Message.Keys.TrailerHeaders[F], trailers.get)
                    .withBodyStream(ChunkedEncoding
                      .decode(message.rest, read, maxHeaderSize, maxHeaderSize, trailers, rest)),
                  rest.get)
            }
          } else {
            Body.parseFixedBody(headerP.contentLength.getOrElse(0L), message.rest, read).map {
              case (bodyStream, drain) =>
                (baseReq.withBodyStream(bodyStream), drain)
            }
          }
      } yield request
  }

  object Response {

    def parser[F[_]: Concurrent](maxHeaderSize: Int)(
        buffer: Array[Byte],
        read: Read[F]
    ): F[(Response[F], Drain[F])] = {
      // per https://httpwg.org/specs/rfc7230.html#rfc.section.3.3.3
      def expectNoBody(status: Status): Boolean =
        status == Status.NoContent ||
          status == Status.NotModified ||
          status.responseClass == Status.Informational

      for {
        message <- MessageP.parseMessage(buffer, read, maxHeaderSize)
        prelude <- RespPrelude.parsePrelude(message.bytes)
        headerP <- HeaderP.parseHeaders(message.bytes, prelude.nextIndex)

        baseResp = org.http4s.Response[F](
          httpVersion = prelude.version,
          status = prelude.status,
          headers = headerP.headers
        )

        resp <-
          if (headerP.chunked) {
            Ref.of[F, Option[Array[Byte]]](None).product(Deferred[F, Headers]).map {
              case (rest, trailers) =>
                baseResp
                  .withAttribute(Message.Keys.TrailerHeaders[F], trailers.get)
                  .withBodyStream(ChunkedEncoding
                    .decode(message.rest, read, maxHeaderSize, maxHeaderSize, trailers, rest)) ->
                  rest.get
            }
          } else if (expectNoBody(prelude.status)) {
            (baseResp -> (Some(message.rest): Option[Array[Byte]]).pure[F]).pure[F]
          } else {
            headerP.contentLength
              .fold(Body.parseUnknownBody(message.rest, read))(
                Body.parseFixedBody(_, message.rest, read)
              )
              .map { case (bodyStream, drain) =>
                baseResp.withBodyStream(bodyStream) -> drain
              }
          }
      } yield resp
    }

    object RespPrelude {

      final case class RespPrelude(version: HttpVersion, status: Status, nextIndex: Int)

      // HTTP/1.1 200 OK
      // Status-Line = HTTP-Version SP Status-Code SP Reason-Phrase CRLF
      def parsePrelude[F[_]](buffer: Array[Byte])(implicit F: MonadThrow[F]): F[RespPrelude] = {
        var complete = false
        var idx = 0
        var throwable: Throwable = null
        var httpVersion: HttpVersion = null

        var codeS: String = null
        // val reason: String = null
        var status: Status = null
        var start = 0
        var state = 0 // 0 Is for HttpVersion, 1 for Status Code, 2 For Reason Phrase

        while (!complete && idx < buffer.size) {
          val value = buffer(idx)
          (state: @switch) match {
            case 0 =>
              if (value == space) {
                val s = new String(buffer, start, idx - start)
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
                codeS = new String(buffer, start, idx - start)
                state = 2
                start = idx + 1
              }
            case 2 =>
              if (value == lf && (idx > 0 && buffer(idx - 1) == cr)) {
                val reason = new String(buffer, start, idx - start - 1)
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

        if (throwable != null)
          F.raiseError(RespPreludeError("Encountered Error parsing", Option(throwable)))
        else if (httpVersion == null || status == null)
          F.raiseError(RespPreludeError("Failed to parse HTTP response prelude", None))
        else
          RespPrelude(httpVersion, status, idx).pure[F]
      }

      case class RespPreludeError(message: String, cause: Option[Throwable])
          extends Exception(
            s"Received Error while parsing prelude - Message: $message - ${cause.map(_.getMessage)}",
            cause.orNull)
    }
  }

  object Body {
    def parseFixedBody[F[_]: Concurrent](
        contentLength: Long,
        buffer: Array[Byte],
        read: Read[F]): F[(EntityBody[F], Drain[F])] =
      if (contentLength > 0) {
        if (buffer.length >= contentLength) {
          val (body, rest) = buffer.splitAt(contentLength.toInt)
          (Stream.chunk(Chunk.bytes(body)).covary[F], (Some(rest): Option[Array[Byte]]).pure[F])
            .pure[F]
        } else {
          val unread = contentLength - buffer.length
          Ref.of[F, Either[Long, Array[Byte]]](Left(unread)).map { state =>
            val bodyStream = Stream.eval(state.get).flatMap {
              case Right(_) =>
                Stream.raiseError(BodyAlreadyConsumedError())
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
            val drain: Drain[F] = state.get.map(_.toOption)

            (Stream.chunk(Chunk.bytes(buffer)) ++ bodyStream, drain)
          }
        }
      } else {
        (EmptyBody.covary[F], (Some(buffer): Option[Array[Byte]]).pure[F]).pure[F]
      }

    def parseUnknownBody[F[_]: Concurrent](
        buffer: Array[Byte],
        read: Read[F]
    ): F[(EntityBody[F], Drain[F])] =
      Ref[F].of(false).map { consumed =>
        lazy val readAll: Pull[F, Byte, Unit] =
          Pull.eval(read).flatMap {
            case Some(c) => Pull.output(c) >> readAll
            case None => Pull.eval(consumed.set(true)).void
          }

        val body =
          Stream
            .eval(consumed.get)
            .ifM(
              ifTrue = Stream.raiseError(BodyAlreadyConsumedError()),
              ifFalse = Stream.chunk(Chunk.array(buffer)) ++ readAll.stream
            )

        val drain: Drain[F] = (None: Option[Array[Byte]]).pure[F]

        (body, drain)
      }

    final case class BodyAlreadyConsumedError()
        extends Exception("Body Has Been Consumed Completely Already")

    private def readStream[F[_]](read: Read[F]): Stream[F, Byte] =
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

  private[this] val space = ' '.toByte
  private[this] val cr: Byte = '\r'.toByte
  private[this] val lf: Byte = '\n'.toByte
}
