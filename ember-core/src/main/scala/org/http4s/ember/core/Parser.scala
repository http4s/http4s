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
import cats.effect.kernel.{Concurrent, Deferred, Ref}
import cats.syntax.all._
import fs2._
import org.http4s._
import org.typelevel.ci.CIString
import scala.annotation.switch
import scala.collection.mutable
import scodec.bits.ByteVector

private[ember] object Parser {

  object HeaderP {

    def parseHeaders[F[_]](
        head: Array[Byte],
        read: Read[F],
        maxHeaderLength: Int,
        acc: Option[ParseHeadersIncomplete])(implicit
        F: MonadThrow[F]): F[(Headers, Boolean, Option[Long], Array[Byte])] = {
      // TODO: improve this
      val nextChunk = if (head.nonEmpty) F.pure(Some(Chunk.byteVector(ByteVector(head)))) else read
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
              F.pure((headers, chunked, length, rest))
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
        accHeaders: List[Header.Raw],
        idx: Int,
        state: Boolean,
        name: Option[String],
        start: Int,
        chunked: Boolean,
        contentLength: Option[Long])
        extends ParseHeaderResult

    def headersInSection(
        bv: Array[Byte],
        initIndex: Int = 0,
        initState: Boolean = false, //HeaderNameOrPostCRLF,
        initHeaders: List[Header.Raw] = List.empty,
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
        if (!state) {
          val current = bv(idx)
          // if current index is colon our name is complete
          if (current == colon) {
            state = true // set state to check for header value
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
        } else {
          val current = bv(idx)
          // If crlf is next we have completed the header value
          if (current == lf && (idx > 0 && bv(idx - 1) == cr)) {
            // extract header value, trim leading and trailing whitespace
            val hValue = new String(bv, start, idx - start - 1).trim

            val hName = name // copy var to val
            name = null // set name back to null
            val newHeader = Header.Raw(CIString(hName), hValue) // create header
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
            state = false // Go back to Looking for HeaderName or Termination
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

    object ReqPrelude {

      def parsePrelude[F[_]](
          head: Array[Byte],
          read: Read[F],
          maxHeaderLength: Int,
          acc: Option[ParsePreludeIncomplete] = None)(implicit
          F: MonadThrow[F]): F[(Method, Uri, HttpVersion, Array[Byte])] = {
        val nextChunk =
          if (head.nonEmpty) F.pure(Some(Chunk.byteVector(ByteVector(head)))) else read

        nextChunk.flatMap {
          case Some(chunk) =>
            val next: Array[Byte] = acc match {
              case None => chunk.toArray
              case Some(ic) => combineArrays(ic.bv, chunk.toArray)
            }
            ReqPrelude.preludeInSection(next) match {
              case ParsePreludeComplete(m, u, h, rest) =>
                F.pure((m, u, h, rest))
              case t @ ParsePreludeError(_, _, _, _, _) => F.raiseError(t)
              case p @ ParsePreludeIncomplete(_, _, method, uri, httpVersion) =>
                if (next.size <= maxHeaderLength)
                  parsePrelude(Array.emptyByteArray, read, maxHeaderLength, p.some)
                else
                  F.raiseError(
                    ParsePreludeError(
                      "Reached Max Header Length Looking for Request Prelude",
                      None,
                      method,
                      uri,
                      httpVersion))
            }
          case None =>
            acc match {
              case None => F.raiseError(EmptyStreamError())
              case Some(incomplete) if incomplete.bv.isEmpty => F.raiseError(EmptyStreamError())
              case Some(incomplete) =>
                F.raiseError(
                  ParsePreludeError(
                    s"Unexpected EOF - $incomplete",
                    None,
                    incomplete.method,
                    incomplete.uri,
                    incomplete.httpVersion))
            }
        }
      }

      // sealed trait ParsePreludeState
      // 0 case object GetMethod extends ParsePreludeState
      // 1 case object GetUri extends ParsePreludeState
      // 2 case object GetHttpVersion extends ParsePreludeState
      private val space = ' '.toByte
      private val cr: Byte = '\r'.toByte
      private val lf: Byte = '\n'.toByte

      sealed trait ParsePreludeResult
      case class ParsePreludeError(
          message: String,
          caused: Option[Throwable],
          method: Option[Method],
          uri: Option[Uri],
          httpVersion: Option[HttpVersion]
      ) extends Exception(
            s"Parse Prelude Error Encountered - Message: $message - Partially Decoded: $method $uri $httpVersion",
            caused.orNull
          )
          with ParsePreludeResult
      final case class ParsePreludeIncomplete(
          idx: Int,
          bv: Array[Byte],
          // buffer: String,
          method: Option[Method],
          uri: Option[Uri],
          httpVersion: Option[HttpVersion]
      ) extends ParsePreludeResult
      final case class ParsePreludeComplete(
          method: Method,
          uri: Uri,
          httpVersion: HttpVersion,
          rest: Array[Byte]
      ) extends ParsePreludeResult
      // Method SP URI SP HttpVersion CRLF - REST
      def preludeInSection(
          bv: Array[Byte],
          initIdx: Int = 0,
          initMethod: Option[Method] = None,
          initUri: Option[Uri] = None,
          initHttpVersion: Option[HttpVersion] = None
      ): ParsePreludeResult = {
        var idx = initIdx
        var state: Byte = 0
        var complete = false

        var throwable: Throwable = null
        var method: Method = initMethod.orNull
        var uri: Uri = initUri.orNull
        var httpVersion: HttpVersion = initHttpVersion.orNull

        var start = 0
        while (!complete && idx < bv.size) {
          val value = bv(idx)
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
          ParsePreludeError(
            throwable.getMessage(),
            Option(throwable),
            Option(method),
            Option(uri),
            Option(httpVersion)
          )
        else if (method != null && uri != null && httpVersion != null)
          ParsePreludeComplete(method, uri, httpVersion, bv.drop(idx))
        else
          ParsePreludeIncomplete(idx, bv, Option(method), Option(uri), Option(httpVersion))
      }
    }

    def parser[F[_]](maxHeaderLength: Int)(
        head: Array[Byte],
        read: Read[F]
    )(implicit F: Concurrent[F]): F[(Request[F], Drain[F])] =
      ReqPrelude
        .parsePrelude[F](head, read, maxHeaderLength, None)
        .flatMap { case (method, uri, httpVersion, bytes) =>
          HeaderP.parseHeaders(bytes, read, maxHeaderLength, None).flatMap {
            case (headers, chunked, contentLength, bytes) =>
              val baseReq: org.http4s.Request[F] = org.http4s.Request[F](
                method = method,
                uri = uri,
                httpVersion = httpVersion,
                headers = headers
              )

              if (chunked) {
                Ref.of[F, Option[Array[Byte]]](None).product(Deferred[F, Headers]).map {
                  case (rest, trailers) =>
                    (
                      baseReq
                        .withAttribute(Message.Keys.TrailerHeaders[F], trailers.get)
                        .withBodyStream(
                          ChunkedEncoding.decode(bytes, read, maxHeaderLength, trailers, rest)),
                      rest.get)
                }
              } else {
                Body.parseFixedBody(contentLength.getOrElse(0L), bytes, read).map {
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
        read: Read[F]
    ): F[(Response[F], Drain[F])] =
      RespPrelude
        .parsePrelude(head, read, maxHeaderLength, None)
        .flatMap { case (httpVersion, status, bytes) =>
          HeaderP.parseHeaders(bytes, read, maxHeaderLength, None).flatMap {
            case (headers, chunked, contentLength, bytes) =>
              val baseResp = org.http4s.Response[F](
                httpVersion = httpVersion,
                status = status,
                headers = headers
              )

              if (chunked) {
                Ref.of[F, Option[Array[Byte]]](None).product(Deferred[F, Headers]).map {
                  case (rest, trailers) =>
                    (
                      baseResp
                        .withAttribute(Message.Keys.TrailerHeaders[F], trailers.get)
                        .withBodyStream(
                          ChunkedEncoding.decode(bytes, read, maxHeaderLength, trailers, rest)),
                      rest.get)
                }
              } else {
                Body.parseFixedBody(contentLength.getOrElse(0L), bytes, read).map {
                  case (bodyStream, drain) =>
                    (baseResp.withBodyStream(bodyStream), drain)
                }
              }
          }
        }

    object RespPrelude {

      def parsePrelude[F[_]](
          head: Array[Byte],
          read: Read[F],
          maxHeaderLength: Int,
          acc: Option[Array[Byte]] = None)(implicit
          F: MonadThrow[F]): F[(HttpVersion, Status, Array[Byte])] = {
        val pull = if (head.nonEmpty) F.pure(Some(Chunk.byteVector(ByteVector(head)))) else read

        pull.flatMap {
          case Some(chunk) =>
            val next: Array[Byte] = acc match {
              case None => chunk.toArray
              case Some(remains) => combineArrays(remains, chunk.toArray)
            }
            preludeInSection(next) match {
              case RespPreludeComplete(httpVersion, status, rest) =>
                (httpVersion, status, rest).pure[F]
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
              case None => F.raiseError(EmptyStreamError())
              case Some(incomplete) if incomplete.isEmpty => F.raiseError(EmptyStreamError())
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

        if (throwable != null) RespPreludeError("Encountered Error parsing", Option(throwable))
        if (httpVersion != null && status != null)
          RespPreludeComplete(httpVersion, status, bv.drop(idx))
        else RespPreludeIncomplete
      }
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
          (
            Stream.chunk(Chunk.byteVector(ByteVector(body))).covary[F],
            (Some(rest): Option[Array[Byte]]).pure[F])
            .pure[F]
        } else {
          val unread = contentLength - buffer.length
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
            val drain: Drain[F] = state.get.map(_.toOption)

            (Stream.chunk(Chunk.byteVector(ByteVector(buffer))) ++ bodyStream, drain)
          }
        }
      } else {
        (EmptyBody.covary[F], (Some(buffer): Option[Array[Byte]]).pure[F]).pure[F]
      }

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
}
