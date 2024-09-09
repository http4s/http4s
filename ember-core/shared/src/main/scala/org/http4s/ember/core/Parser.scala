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
import cats.effect.kernel.Concurrent
import cats.effect.kernel.Deferred
import cats.effect.kernel.Ref
import cats.syntax.all._
import fs2._
import org.http4s._
import org.typelevel.ci.CIString
import scodec.bits.ByteVector

import scala.annotation.switch
import scala.util.control.NonFatal

private[ember] object Parser {

  object MessageP {

    def recurseFind[F[_], S, A](
        currentBuffer: Array[Byte],
        read: Read[F],
        maxHeaderSize: Int,
        state: S,
    )(
        f: (S, Array[Byte]) => F[Either[S, A]]
    )(idx: A => Int)(implicit F: MonadThrow[F]): F[(A, Array[Byte])] =
      f(state, currentBuffer).flatMap {
        case Left(s) =>
          if (currentBuffer.length > maxHeaderSize)
            F.raiseError(EmberException.MessageTooLong(maxHeaderSize))
          else {
            read.flatMap {
              case Some(chunk) =>
                val nextBuffer = Util.concatBytes(currentBuffer, chunk)
                recurseFind(nextBuffer, read, maxHeaderSize, s)(f)(idx)
              case None if currentBuffer.length > 0 =>
                F.raiseError(EmberException.ReachedEndOfStream())
              case _ => F.raiseError(EmberException.EmptyStream())
            }
          }
        case Right(out) =>
          (out, currentBuffer.drop(idx(out))).pure[F]
      }

  }

  final case class HeaderP(
      headers: Headers,
      chunked: Boolean,
      contentLength: Option[Long],
      idx: Int,
  )

  object HeaderP {
    private[this] final val colon = 58 // ':'
    private[this] final val contentLengthS = "Content-Length"
    private[this] final val transferEncodingS = "Transfer-Encoding"
    private[this] final val chunkedS = "chunked"

    final case class ParserState(
        idx: Int,
        state: Boolean,
        throwable: Option[Throwable],
        complete: Boolean,
        chunked: Boolean,
        contentLength: Option[Long],
        headers: List[Header.Raw],
        name: Option[String],
        start: Int,
    )

    object ParserState {
      def initial: ParserState = ParserState(
        idx = 0,
        state = false,
        throwable = None,
        complete = false,
        chunked = false,
        contentLength = None,
        headers = List.empty,
        name = None,
        start = 0,
      )
    }

    def parse[F[_]](message: Array[Byte], maxHeaderSize: Int, s: ParserState)(implicit
        F: MonadThrow[F]
    ): F[Either[ParserState, HeaderP]] = {
      var idx: Int = s.idx
      var state = s.state
      var throwable: Throwable = s.throwable.orNull
      var complete = s.complete
      var chunked: Boolean = s.chunked
      var contentLength: Option[Long] = s.contentLength

      var headers: List[Header.Raw] = s.headers
      var name: String = s.name.orNull
      var start: Int = s.start
      val upperBound = Math.min(message.size - 1, maxHeaderSize)

      while (!complete && idx <= upperBound) {
        if (!state) {
          val current = message(idx)
          // if current index is colon our name is complete
          if (current == colon) {
            state = true // set state to check for header value
            name = new String(message, start, idx - start) // extract name string
            start = idx + 1 // advance past colon for next start

            // TODO: This if clause may not be necessary since the header value parser trims
            if (message.size > idx + 1 && message(idx + 1) == space) {
              start += 1 // if colon is followed by space advance again
              idx += 1 // double advance index here to skip the space
            }
            // double CRLF condition - Termination of headers
          } else if (current == lf && (idx > 0 && message(idx - 1) == cr)) {
            complete = true // completed terminate loop
          }
        } else {
          val current = message(idx)
          // If crlf is next we have completed the header value
          if (current == lf && (idx > 0 && message(idx - 1) == cr)) {
            // extract header value, trim leading and trailing whitespace
            val hValue = new String(message, start, idx - start - 1).trim

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
            } else if (
              hName
                .equalsIgnoreCase(transferEncodingS)
            ) { // Check if this is Transfer-encoding
              chunked = hValue.contains(chunkedS)
            }
            start = idx + 1 // Next Start is after the CRLF
            headers = newHeader :: headers // Add Header
            state = false // Go back to Looking for HeaderName or Termination
          }
        }
        idx += 1 // Single Advance Every Iteration
      }

      if (throwable != null) {
        F.raiseError(ParseHeadersError(throwable))
      } else if (!complete) {
        ParserState(
          idx,
          state,
          Option(throwable),
          complete,
          chunked,
          contentLength,
          headers,
          Option(name),
          start,
        ).asLeft.pure[F]
      } else {
        HeaderP(Headers(headers.reverse), chunked, contentLength, idx).asRight.pure[F]
      }
    }

    final case class ParseHeadersError(cause: Throwable)
        extends Exception(
          s"Encountered Error Attempting to Parse Headers - ${cause.getMessage}",
          cause,
        )

    final case class IncompleteHttpMessage(headers: Headers)
        extends Exception("Tried To Parse An Incomplete HTTP Message")
  }

  object Request {

    final case class ReqPrelude(method: Method, uri: Uri, version: HttpVersion, nextIndex: Int)

    object ReqPrelude {

      final case class ParserState(
          idx: Int,
          state: Byte,
          complete: Boolean,
          throwable: Option[Throwable],
          method: Option[Method],
          uri: Option[Uri],
          httpVersion: Option[HttpVersion],
          start: Int,
      )

      object ParserState {
        def initial: ParserState = ParserState(
          idx = 0,
          state = 0,
          complete = false,
          throwable = None,
          method = None,
          httpVersion = None,
          uri = None,
          start = 0,
        )
      }

      def parse[F[_]](message: Array[Byte], maxHeaderSize: Int, s: ParserState)(implicit
          F: MonadThrow[F]
      ): F[Either[ParserState, ReqPrelude]] = {
        var idx = s.idx
        var state: Byte = s.state
        var complete = s.complete

        var throwable: Throwable = s.throwable.orNull
        var method: Method = s.method.orNull
        var uri: Uri = s.uri.orNull
        var httpVersion: HttpVersion = s.httpVersion.orNull

        var start = s.start
        val upperBound = Math.min(message.size - 1, maxHeaderSize)
        while (!complete && idx <= upperBound) {
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
              Option(httpVersion),
            )
          )
        else if (method == null || uri == null || httpVersion == null)
          ParserState(
            idx,
            state,
            complete,
            Option(throwable),
            Option(method),
            Option(uri),
            Option(httpVersion),
            start,
          ).asLeft
            .pure[F]
        else
          ReqPrelude(method, uri, httpVersion, idx).asRight.pure[F]
      }

      final case class ParsePreludeError(
          message: String,
          caused: Option[Throwable],
          method: Option[Method],
          uri: Option[Uri],
          httpVersion: Option[HttpVersion],
      ) extends Exception(
            s"Parse Prelude Error Encountered - Message: $message - Partially Decoded: $method $uri $httpVersion",
            caused.orNull,
          )
    }

    def parser[F[_]](maxHeaderSize: Int)(
        buffer: Array[Byte],
        read: Read[F],
    )(implicit F: Concurrent[F]): F[(Request[F], Drain[F])] =
      for {
        x <- MessageP.recurseFind(buffer, read, maxHeaderSize, ReqPrelude.ParserState.initial)(
          (state, ibuffer) => ReqPrelude.parse(ibuffer, maxHeaderSize, state)
        )(_.nextIndex)
        (prelude, buffer2) = x
        y <- MessageP
          .recurseFind(
            buffer2,
            read,
            maxHeaderSize,
            HeaderP.ParserState.initial,
          )((state, ibuffer) => HeaderP.parse(ibuffer, maxHeaderSize, state))(_.idx)
          // We've already consumed data to parse the prelude so empty actually means end of stream
          .adaptError { case _: EmberException.EmptyStream =>
            EmberException.ReachedEndOfStream()
          }
        (headerP, finalBuffer) = y

        baseReq = org.http4s.Request[F](
          method = prelude.method,
          uri = prelude.uri,
          httpVersion = prelude.version,
          headers = headerP.headers,
        )

        request <-
          if (headerP.chunked) {
            Ref.of[F, Option[Array[Byte]]](None).product(Deferred[F, Headers]).map {
              case (rest, trailers) =>
                (
                  baseReq
                    .withAttribute(Message.Keys.TrailerHeaders[F], trailers.get)
                    .withBodyStream(
                      ChunkedEncoding
                        .decode(finalBuffer, read, maxHeaderSize, maxHeaderSize, trailers, rest)
                    ),
                  rest.get,
                )
            }
          } else {
            Body.parseFixedBody(headerP.contentLength.getOrElse(0), finalBuffer, read).map {
              case (bodyEntity, drain) =>
                (baseReq.withEntity(bodyEntity), drain)
            }
          }
      } yield request
  }

  object Response {

    def parser[F[_]: Concurrent](maxHeaderSize: Int)(
        buffer: Array[Byte],
        read: Read[F],
    ): F[(Response[F], Drain[F])] = parser[F](maxHeaderSize, discardBody = false)(buffer, read)

    def parser[F[_]: Concurrent](maxHeaderSize: Int, discardBody: Boolean)(
        buffer: Array[Byte],
        read: Read[F],
    ): F[(Response[F], Drain[F])] = {
      // per https://httpwg.org/specs/rfc7230.html#rfc.section.3.3.3
      def expectNoBody(status: Status): Boolean =
        status == Status.NoContent ||
          status == Status.NotModified ||
          status.responseClass == Status.Informational

      for {
        x <- MessageP.recurseFind(buffer, read, maxHeaderSize, RespPrelude.ParserState.initial)(
          (state, ibuffer) => RespPrelude.parse(ibuffer, maxHeaderSize, state)
        )(_.nextIndex)
        (prelude, buffer2) = x
        y <- MessageP
          .recurseFind(
            buffer2,
            read,
            maxHeaderSize,
            HeaderP.ParserState.initial,
          )((state, ibuffer) => HeaderP.parse(ibuffer, maxHeaderSize, state))(_.idx)
          // We've already consumed data to parse the prelude so empty actually means end of stream
          .adaptError { case _: EmberException.EmptyStream =>
            EmberException.ReachedEndOfStream()
          }

        (headerP, finalBuffer) = y

        baseResp = org.http4s.Response[F](
          httpVersion = prelude.version,
          status = prelude.status,
          headers = headerP.headers,
        )

        resp <-
          if (discardBody) {
            (baseResp -> none[Array[Byte]].pure[F]).pure[F]
          } else if (expectNoBody(prelude.status)) {
            (baseResp -> finalBuffer.some.pure[F]).pure[F]
          } else if (headerP.chunked) {
            Ref.of[F, Option[Array[Byte]]](None).product(Deferred[F, Headers]).map {
              case (rest, trailers) =>
                baseResp
                  .withAttribute(Message.Keys.TrailerHeaders[F], trailers.get)
                  .withBodyStream(
                    ChunkedEncoding
                      .decode(finalBuffer, read, maxHeaderSize, maxHeaderSize, trailers, rest)
                  ) ->
                  rest.get
            }
          } else {
            headerP.contentLength
              .fold(Body.parseUnknownBody(finalBuffer, read))(
                Body.parseFixedBody(_, finalBuffer, read)
              )
              .map { case (bodyEntity, drain) =>
                baseResp.withEntity(bodyEntity) -> drain
              }
          }
      } yield resp
    }

    object RespPrelude {

      final case class RespPrelude(version: HttpVersion, status: Status, nextIndex: Int)

      final case class ParserState(
          idx: Int,
          state: Byte,
          complete: Boolean,
          throwable: Option[Throwable],
          httpVersion: Option[HttpVersion],
          codeS: Option[String],
          status: Option[Status],
          start: Int,
      )

      object ParserState {
        def initial: ParserState = ParserState(
          idx = 0,
          state = 0,
          complete = false,
          throwable = None,
          httpVersion = None,
          codeS = None,
          status = None,
          start = 0,
        )
      }

      // HTTP/1.1 200 OK
      // Status-Line = HTTP-Version SP Status-Code SP Reason-Phrase CRLF
      def parse[F[_]](buffer: Array[Byte], maxHeaderSize: Int, s: ParserState)(implicit
          F: MonadThrow[F]
      ): F[Either[ParserState, RespPrelude]] = {
        var complete = s.complete
        var idx = s.idx
        var throwable: Throwable = s.throwable.orNull
        var httpVersion: HttpVersion = s.httpVersion.orNull

        var codeS: String = s.codeS.orNull
        // val reason: String = null
        var status: Status = s.status.orNull
        var start = s.start
        var state = s.state // 0 Is for HttpVersion, 1 for Status Code, 2 For Reason Phrase
        val upperBound = Math.min(buffer.size - 1, maxHeaderSize)

        while (!complete && idx <= upperBound) {
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
                val codeInt = codeS.toInt
                Status.fromInt(codeInt) match {
                  case Left(e) =>
                    if (NonFatal(e)) {
                      throwable = e
                      complete = true
                    } else
                      throw e
                  case Right(st) =>
                    status = st
                    complete = true
                }
              }
          }
          idx += 1
        }

        if (throwable != null)
          F.raiseError(RespPreludeError("Encountered Error parsing", Option(throwable)))
        else if (httpVersion == null || status == null)
          ParserState(
            idx,
            state,
            complete,
            Option(throwable),
            Option(httpVersion),
            Option(codeS),
            Option(status),
            start,
          ).asLeft
            .pure[F]
        else
          RespPrelude(httpVersion, status, idx).asRight.pure[F]
      }

      final case class RespPreludeError(message: String, cause: Option[Throwable])
          extends Exception(
            s"Received Error while parsing prelude - Message: $message - ${cause.map(_.getMessage)}",
            cause.orNull,
          )
    }
  }

  object Body {
    def parseFixedBody[F[_]](
        contentLength: Long,
        buffer: Array[Byte],
        read: Read[F],
    )(implicit F: Concurrent[F]): F[(Entity[F], Drain[F])] =
      if (contentLength > 0) {
        if (buffer.length == contentLength) {
          val drain: Drain[F] = F.pure(Some(Array.emptyByteArray): Option[Array[Byte]])
          F.pure(Entity.strict(ByteVector.view(buffer)) -> drain)
        } else if (buffer.length > contentLength) {
          val drain: Drain[F] = F.pure(Some(buffer.drop(contentLength.toInt)): Option[Array[Byte]])
          F.pure(Entity.strict(ByteVector.view(buffer, 0, contentLength.toInt)) -> drain)
        } else {
          val unread = contentLength - buffer.length
          Ref.of[F, Option[Array[Byte]]](None).map { state =>
            val body = decode(unread, buffer, state, read)
            // If the remaining bytes for the body have not yet been read, close the connection.
            // followup: Check if there are bytes immediately available without blocking
            val drain: Drain[F] = state.get
            (Entity.stream(body), drain)
          }
        }
      } else
        F.pure(Entity.empty[F] -> F.pure(Some(buffer)))

    def parseUnknownBody[F[_]: Concurrent](
        buffer: Array[Byte],
        read: Read[F],
    ): F[(Entity[F], Drain[F])] =
      Ref[F].of(false).map { consumed =>
        lazy val readAll: Pull[F, Byte, Unit] =
          Pull.eval(read).flatMap {
            case Some(c) => Pull.output(c) >> readAll
            case None => Pull.eval(consumed.set(true)).void
          }

        val body =
          Pull
            .eval(consumed.get)
            .flatMap {
              case true => Pull.raiseError(BodyAlreadyConsumedError())
              case false => Pull.output(Chunk.array(buffer)) >> readAll
            }
            .stream

        val drain: Drain[F] = (None: Option[Array[Byte]]).pure[F]

        (Entity.stream(body), drain)
      }

    final case class BodyAlreadyConsumedError()
        extends Exception("Body Has Been Consumed Completely Already")

    def decode[F[_]: Concurrent](
        unread: Long,
        buffer: Array[Byte],
        nextBuffer: Ref[F, Option[Array[Byte]]],
        read: Read[F],
    ): Stream[F, Byte] = {
      def go(remaining: Long): Pull[F, Byte, Unit] =
        Pull.eval(read).flatMap {
          case Some(chunk) =>
            if (chunk.size >= remaining) {
              val (rest, after) = chunk.splitAt(remaining.toInt)
              Pull.eval(nextBuffer.set(Some(after.toArray))) >> Pull.output(rest) >> Pull.done
            } else {
              Pull.output(chunk) >> go(remaining - chunk.size)
            }
          case None => Pull.raiseError(EmberException.ReachedEndOfStream())
        }

      // TODO: This doesn't forbid concurrent reads of the body stream, only sequential reads.
      val pull = Pull.eval(nextBuffer.get).flatMap {
        case Some(_) =>
          Pull.raiseError(BodyAlreadyConsumedError())
        case None =>
          Pull.output(Chunk.array(buffer)) >> go(unread)
      }

      pull.stream
    }
  }

  private[this] final val space = 32 // ' '
  private[this] final val cr = 13 // '\r'
  private[this] final val lf = 10 // '\n'
}
