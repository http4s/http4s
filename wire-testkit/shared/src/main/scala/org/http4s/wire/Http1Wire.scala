/*
 * Copyright 2026 http4s.org
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

package org.http4s.wire

import cats.effect.Async
import cats.syntax.all._
import scodec.bits.ByteVector

/** Independent HTTP/1 message framing, following RFC 9112 Sections 2, 5, 6, and 7. */
object Http1Wire {
  final case class Response(
      status: Int,
      headers: Vector[(String, String)],
      body: ByteVector,
      trailers: Vector[(String, String)] = Vector.empty,
  )

  final case class Request(
      method: String,
      target: String,
      headers: Vector[(String, String)],
      body: ByteVector,
      trailers: Vector[(String, String)] = Vector.empty,
  )

  val crlf: ByteVector = ByteVector(13, 10)
  private val MaxHeaderBytes = 65536
  private val MaxBodyBytes = 16 * 1024 * 1024

  def ascii(value: String): ByteVector =
    ByteVector.encodeAscii(value).fold(throw _, identity)

  /** Builds exactly the supplied start line, headers, and body; no framing is inferred. */
  def message(startLine: String, headers: Vector[(String, String)], body: ByteVector): ByteVector =
    ascii(
      (Vector(startLine) ++ headers.map { case (k, v) => s"$k: $v" }).mkString("\r\n") +
        "\r\n\r\n"
    ) ++ body

  /** Reads a single response, including a bodyless informational response if one is sent. */
  def readResponse[F[_]: Async](
      peer: RawPeer[F],
      requestMethod: String = "GET",
  ): F[Response] =
    for {
      line <- readLine(peer)
      status <- Async[F].delay(parseStatus(line))
      headers <- readHeaders(peer)
      noBody = requestMethod == "HEAD" || status < 200 || status == 204 || status == 304 ||
        (requestMethod == "CONNECT" && status >= 200 && status < 300)
      content <-
        if (noBody) (ByteVector.empty -> Vector.empty[(String, String)]).pure[F]
        else readBody(peer, headers, request = false)
    } yield Response(status, headers, content._1, content._2)

  def readFinalResponse[F[_]: Async](
      peer: RawPeer[F],
      requestMethod: String = "GET",
  ): F[Response] = {
    def loop(remaining: Int): F[Response] =
      readResponse(peer, requestMethod).flatMap { response =>
        if (response.status >= 200 || response.status == 101) response.pure[F]
        else if (remaining == 0) limit("too many informational responses")
        else loop(remaining - 1)
      }
    loop(16)
  }

  def readRequest[F[_]: Async](peer: RawPeer[F]): F[Request] =
    for {
      line <- readLine(peer)
      parts <- Async[F].delay {
        val split = line.split(" ", -1)
        check(split.length == 3, "malformed request line")
        check(split(0).nonEmpty && split(0).forall(tokenChar), "invalid request method")
        check(split(1).nonEmpty && split(1).forall(c => c > ' ' && c < 127), "invalid target")
        check(version(split(2)), "unsupported HTTP version")
        (split(0), split(1))
      }
      headers <- readHeaders(peer)
      content <- readBody(peer, headers, request = true)
    } yield Request(parts._1, parts._2, headers, content._1, content._2)

  private def fail[F[_]: Async, A](message: String): F[A] =
    Async[F].raiseError(new WireDecodingFailure(message))

  private def limit[F[_]: Async, A](message: String): F[A] =
    Async[F].raiseError(new WireObservationLimit(message))

  private def check(condition: Boolean, message: String): Unit =
    if (!condition) throw new WireDecodingFailure(message)

  private def version(value: String): Boolean = value == "HTTP/1.0" || value == "HTTP/1.1"

  private def tokenChar(c: Char): Boolean =
    (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') ||
      "!#$%&'*+-.^_`|~".contains(c)

  private def trimOws(value: String): String =
    value
      .dropWhile(c => c == ' ' || c == '\t')
      .reverse
      .dropWhile(c => c == ' ' || c == '\t')
      .reverse

  private def readLine[F[_]: Async](peer: RawPeer[F]): F[String] =
    WireDecodingFailure
      .read(peer.readUntil(crlf, 8192))
      .map(_.toArray.iterator.map(b => (b & 0xff).toChar).mkString)

  private def parseStatus(line: String): Int = {
    check(
      line.length >= 13 && version(line.take(8)) && line(8) == ' ' && line(12) == ' ',
      "malformed response status line",
    )
    val digits = line.substring(9, 12)
    check(digits.forall(c => c >= '0' && c <= '9'), "invalid response status")
    val status = digits.toInt
    check(status >= 100 && status <= 599, "invalid response status")
    check(line.drop(13).forall(c => c == '\t' || c >= ' ' && c != 127), "invalid reason phrase")
    status
  }

  private def parseHeader(line: String): (String, String) = {
    val colon = line.indexOf(':')
    check(colon > 0 && line.take(colon).forall(tokenChar), "invalid header field name")
    val value = trimOws(line.drop(colon + 1))
    check(value.forall(c => c == '\t' || c >= ' ' && c != 127), "invalid header field value")
    (line.take(colon), value)
  }

  private def readHeaders[F[_]: Async](peer: RawPeer[F]): F[Vector[(String, String)]] = {
    def loop(total: Int, headers: Vector[(String, String)]): F[Vector[(String, String)]] =
      readLine(peer).flatMap { line =>
        val next = total + line.length + 2
        if (next > MaxHeaderBytes) limit("header section too large")
        else if (line.isEmpty) headers.pure[F]
        else if (headers.size >= 256) limit("too many header fields")
        else Async[F].delay(parseHeader(line)).flatMap(h => loop(next, headers :+ h))
      }
    loop(0, Vector.empty)
  }

  private def unsigned(value: String, radix: Int, max: Long): Long = {
    check(value.nonEmpty, "empty framing length")
    value.foldLeft(0L) { (acc, c) =>
      val digit =
        if (c >= '0' && c <= '9') c - '0'
        else if (c >= 'a' && c <= 'f') c - 'a' + 10
        else if (c >= 'A' && c <= 'F') c - 'A' + 10
        else -1
      check(digit >= 0 && digit < radix, "invalid framing length")
      if (acc > (max - digit) / radix || digit > max)
        throw new WireObservationLimit("framing length exceeds limit")
      acc * radix + digit
    }
  }

  /** Validates token/quoted-string parameters without interpreting their extension semantics. */
  private def parameters(value: String, start: Int, allowBare: Boolean): Unit = {
    var i = start
    def ows(): Unit = while (i < value.length && (value(i) == ' ' || value(i) == '\t')) i += 1
    def token(): Unit = {
      val begin = i
      while (i < value.length && tokenChar(value(i))) i += 1
      check(i > begin, "invalid framing extension token")
    }
    while (i < value.length) {
      ows()
      check(i < value.length && value(i) == ';', "invalid framing extension separator")
      i += 1
      ows()
      token()
      ows()
      if (i < value.length && value(i) == '=') {
        i += 1
        ows()
        if (i < value.length && value(i) == '"') {
          i += 1
          while (i < value.length && value(i) != '"') {
            if (value(i) == '\\') {
              i += 1
              check(i < value.length, "truncated quoted extension")
            }
            check(
              value(i) == '\t' || value(i) >= ' ' && value(i) != 127,
              "invalid quoted extension",
            )
            i += 1
          }
          check(i < value.length, "unterminated quoted extension")
          i += 1
        } else token()
      } else check(allowBare, "missing transfer-coding parameter value")
    }
  }

  private def transferCodings(values: Vector[String]): Vector[String] = {
    val input = values.mkString(",")
    val parts = Vector.newBuilder[String]
    var begin = 0
    var quoted = false
    var escaped = false
    var i = 0
    while (i < input.length) {
      val c = input(i)
      if (escaped) escaped = false
      else if (quoted && c == '\\') escaped = true
      else if (c == '"') quoted = !quoted
      else if (!quoted && c == ',') {
        parts += input.substring(begin, i)
        begin = i + 1
      }
      i += 1
    }
    check(!quoted && !escaped, "unterminated transfer-coding parameter")
    parts += input.substring(begin)
    parts.result().map { part =>
      val coding = trimOws(part)
      val token = coding.takeWhile(tokenChar)
      check(token.nonEmpty, "empty transfer coding")
      parameters(coding, token.length, allowBare = false)
      check(
        !token.equalsIgnoreCase("chunked") || token.length == coding.length,
        "chunked transfer coding has parameters",
      )
      token.map(c => if (c >= 'A' && c <= 'Z') (c + ('a' - 'A')).toChar else c)
    }
  }

  private sealed trait Framing
  private case object Chunked extends Framing
  private case object ToEof extends Framing
  private final case class Length(size: Int) extends Framing

  private def framing(headers: Vector[(String, String)], request: Boolean): Framing = {
    val lengths = headers.collect { case (k, v) if k.equalsIgnoreCase("content-length") => v }
    val transfers = headers.collect { case (k, v) if k.equalsIgnoreCase("transfer-encoding") => v }
    check(lengths.isEmpty || transfers.isEmpty, "both transfer-encoding and content-length")
    if (transfers.nonEmpty) {
      val codings = transferCodings(transfers)
      check(codings.count(_ == "chunked") <= 1, "repeated chunked transfer coding")
      if (codings.last == "chunked") Chunked
      else {
        check(!request, "request transfer coding must end in chunked")
        ToEof
      }
    } else if (lengths.nonEmpty) {
      val parsed = lengths
        .flatMap(_.split(",", -1))
        .map(s => unsigned(trimOws(s), 10, MaxBodyBytes.toLong))
      check(parsed.distinct.size == 1, "conflicting content-length values")
      Length(parsed.head.toInt)
    } else if (request) Length(0)
    else ToEof
  }

  private def readBody[F[_]: Async](
      peer: RawPeer[F],
      headers: Vector[(String, String)],
      request: Boolean,
  ): F[(ByteVector, Vector[(String, String)])] =
    Async[F].delay(framing(headers, request)).flatMap {
      case Length(size) =>
        WireDecodingFailure.read(peer.readExact(size)).map(_ -> Vector.empty[(String, String)])
      case ToEof => peer.readToEnd(MaxBodyBytes).map(_ -> Vector.empty[(String, String)])
      case Chunked => readChunks(peer)
    }

  private def readChunks[F[_]: Async](
      peer: RawPeer[F]
  ): F[(ByteVector, Vector[(String, String)])] = {
    def loop(body: ByteVector): F[(ByteVector, Vector[(String, String)])] =
      for {
        line <- readLine(peer)
        size <- Async[F].delay {
          val digits = line.takeWhile(c =>
            c >= '0' && c <= '9' ||
              c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F'
          )
          val size = unsigned(digits, 16, MaxBodyBytes - body.size)
          parameters(line, digits.length, allowBare = true)
          size.toInt
        }
        result <-
          if (size == 0) readHeaders(peer).flatMap { trailers =>
            val invalid = trailers.exists { case (name, _) =>
              name.equalsIgnoreCase("content-length") || name.equalsIgnoreCase("transfer-encoding")
            }
            if (invalid)
              fail[F, (ByteVector, Vector[(String, String)])]("framing field in trailers")
            else (body -> trailers).pure[F]
          }
          else
            for {
              chunk <- WireDecodingFailure.read(peer.readExact(size))
              ending <- WireDecodingFailure.read(peer.readExact(2))
              _ <- if (ending == crlf) Async[F].unit else fail[F, Unit]("missing chunk CRLF")
              next <- loop(body ++ chunk)
            } yield next
      } yield result
    loop(ByteVector.empty)
  }
}
