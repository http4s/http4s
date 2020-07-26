/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.ember.core

import cats._
import cats.implicits._
import fs2._
import scodec.bits.ByteVector
import org.http4s._
import cats.effect._
import Shared._
import _root_.io.chrisdavenport.log4cats.Logger
import org.http4s.ember.core.Parser.HeaderP.Completed
import org.http4s.ember.core.Parser.HeaderP.Incomplete

private[ember] object Parser {

  /**
    * From the stream of bytes this extracts Http Header and body part.
    */
  def httpHeaderAndBody[F[_]](maxHeaderSize: Int)(implicit
      F: ApplicativeError[F, Throwable]): Pipe[F, Byte, (ByteVector, Stream[F, Byte])] = {
    def go(buff: ByteVector, in: Stream[F, Byte]): Pull[F, (ByteVector, Stream[F, Byte]), Unit] =
      in.pull.uncons.flatMap {
        case None =>
          Pull.raiseError[F](
            EmberException.ParseError(
              s"Incomplete Header received (sz = ${buff.size}): ${buff.decodeUtf8}"))
        case Some((chunk, tl)) =>
          val bv = chunk2ByteVector(chunk)
          val all = buff ++ bv
          val idx = all.indexOfSlice(`\r\n\r\n`)
          if (idx < 0)
            if (all.size > maxHeaderSize)
              Pull.raiseError[F](
                EmberException.ParseError(
                  s"Size of the header exceeded the limit of $maxHeaderSize (${all.size})"))
            else go(all, tl)
          else {
            val (h, t) = all.splitAt(idx + 4)
            if (h.size > maxHeaderSize)
              Pull.raiseError[F](
                EmberException.ParseError(
                  s"Size of the header exceeded the limit of $maxHeaderSize (${all.size})"))
            else
              Pull.output1((h, Stream.chunk(Chunk.ByteVectorChunk(t)) ++ tl))
          }
      }
    src => go(ByteVector.empty, src).stream
  }
  
  object HeaderP {
    sealed trait ParseHeaderState
    case object HeaderNameOrPostCRLF extends ParseHeaderState
    case object HeaderValue extends ParseHeaderState
    case object HeadersComplete extends ParseHeaderState
    private val colon: Byte = ':'.toByte
    private val cr: Byte = '\r'.toByte
    private val lf: Byte = '\n'.toByte
    private val space: Byte = ' '.toByte
    sealed trait ParseHeaderResult
    final case class Completed(headers: Headers, rest: ByteVector) extends ParseHeaderResult
    final case class Incomplete(bv: ByteVector, accHeaders: List[Header], partialHeader: (String, String), idx: Long, state: ParseHeaderState) extends ParseHeaderResult

    def headersInSection(
      bv: ByteVector,
      initIndex: Long = 0L,
      initState: ParseHeaderState = HeaderNameOrPostCRLF,
      initPartial: Option[(String, String)] = None,
      initHeaders: List[Header] = List.empty
    ): ParseHeaderResult = {
      import scala.collection.mutable.StringBuilder
      import scala.collection.mutable.ListBuffer
      var idx = initIndex
      var state = initState
      var complete = false
      val headers = new ListBuffer[Header]()
      val name = new StringBuilder()
      val value = new StringBuilder()
      initPartial.foreach{
        case (n, v) => 
          name.append(n)
          value.append(v)
      }
      initHeaders.appendedAll(initHeaders)
      while (!complete && idx < bv.size){
        state match {
          case HeaderNameOrPostCRLF =>
            val current = bv(idx)
            if (current == colon){
              state = HeaderValue
              if ((bv.size >= idx +1) && (bv(idx + 1) == space)){
                idx += 1
              }
            } else if (current == cr && (bv.size >= idx +1) && (bv(idx + 1) == lf)){
              idx += 1
              complete = true
              state = HeadersComplete
            } else {
              name.append(current.toChar)
            }
          case HeaderValue => 
            val current = bv(idx)
            if (current == cr && ((bv.size >= idx +1) && bv(idx +1) == lf)) {
              idx += 1
              val hName = name.toString()
              val hValue = value.toString()
              name.clear()
              value.clear()
              headers += Header(hName, hValue)
              state = HeaderNameOrPostCRLF
            } else {
              value.append(current.toChar)
            }
          case HeadersComplete => 
            complete = true
        }
        idx += 1
      }

      if (state == HeadersComplete)
        Completed(Headers(headers.toList), bv.drop(idx))
      else Incomplete(bv, headers.toList, (name.toString, value.toString), idx, state)
      //throw new Throwable(s"Headers Not Complete ${bv.decodeAscii} - Current: ${headers.toList} - idx: $idx")//(Headers.empty, bv)
    }
  }

  def splitHeader[F[_]: Applicative](byteVector: ByteVector)(
      logger: Logger[F]): F[Either[ByteVector, (ByteVector, ByteVector)]] = {
    val index = byteVector.indexOfSlice(`\r\n`)
    if (index >= 0L) {
      val (line, rest) = byteVector.splitAt(index)
      logger
        .trace(s"splitHeader - Slice: ${line.decodeAscii}- Rest: ${rest.decodeAscii}")
        .as(Either.right[ByteVector, (ByteVector, ByteVector)]((line, rest.drop(`\r\n`.length))))
    } else
      Either.left[ByteVector, (ByteVector, ByteVector)](byteVector).pure[F]
  }

  object Request {
    def parser[F[_]: Sync](maxHeaderLength: Int)(s: Stream[F, Byte])(l: Logger[F]): F[Request[F]] =
      s.through(httpHeaderAndBody[F](maxHeaderLength))
        .evalMap {
          case (bv, body) => headerBlobByteVectorToRequest[F](bv, body, maxHeaderLength)(l)
        }
        .take(1)
        .compile
        .lastOrError

    private def headerBlobByteVectorToRequest[F[_]](
        b: ByteVector,
        s: Stream[F, Byte],
        maxHeaderLength: Int)(logger: Logger[F])(implicit
        F: MonadError[F, Throwable]): F[Request[F]] =
      for {
        shE <- splitHeader(b)(logger)
        (methodHttpUri, headersBV) <- shE.fold(
          _ =>
            ApplicativeError[F, Throwable].raiseError[(ByteVector, ByteVector)](
              EmberException.ParseError("Invalid Empty Init Line")),
          Applicative[F].pure(_)
        )

        (method, uri, http) <- bvToRequestTopLine[F](methodHttpUri)

        // Raw Header Logging
        _ <- logger.trace(s"HeadersSection - ${headersBV.decodeAscii}")

        headers = HeaderP.headersInSection(headersBV) match {
          case Completed(headers, _) => headers
          case Incomplete(_, _,_, _, _) => ???
        }
        _ <- logger.trace(show"Headers: $headers")

        host = headers.get(org.http4s.headers.Host)
        // enriched with host
        // seems presumptious
        newUri = uri.copy(
          authority = host.map(h => Uri.Authority(host = Uri.RegName(h.host), port = h.port)))
        newHeaders = headers.filterNot(_.is(org.http4s.headers.Host))
      } yield {
        val baseReq: org.http4s.Request[F] = org.http4s.Request[F](
          method = method,
          uri = newUri,
          httpVersion = http,
          headers = newHeaders
        )
        val body =
          if (baseReq.isChunked) s.through(ChunkedEncoding.decode(maxHeaderLength))
          else s.take(baseReq.contentLength.getOrElse(0L))
        baseReq.withBodyStream(body)
      }

    private def bvToRequestTopLine[F[_]](b: ByteVector)(implicit
        F: MonadError[F, Throwable]): F[(Method, Uri, HttpVersion)] =
      for {
        (method, rest) <- getMethodEmitRest[F](b)
        (uri, httpVString) <- getUriEmitHttpVersion[F](rest)
        httpVersion <- HttpVersion.fromString(httpVString).liftTo[F]
      } yield (method, uri, httpVersion)

    private def getMethodEmitRest[F[_]](b: ByteVector)(implicit
        F: ApplicativeError[F, Throwable]): F[(Method, String)] = {
      val opt = for {
        line <- b.decodeAscii.toOption
        idx <- Some(line.indexOf(' '))
        if idx >= 0
        out <- Method.fromString(line.substring(0, idx)).toOption
      } yield (out, line.substring(idx + 1))

      opt.fold(
        ApplicativeError[F, Throwable]
          .raiseError[(Method, String)](EmberException.ParseError("Missing Method"))
      )(ApplicativeError[F, Throwable].pure(_))
    }

    private def getUriEmitHttpVersion[F[_]](s: String)(implicit
        F: ApplicativeError[F, Throwable]): F[(Uri, String)] = {
      val opt = for {
        idx <- Some(s.indexOf(' '))
        if idx >= 0
        uri <- Uri.fromString(s.substring(0, idx)).toOption
      } yield (uri, s.substring(idx + 1))

      opt.fold(
        ApplicativeError[F, Throwable]
          .raiseError[(Uri, String)](EmberException.ParseError("Missing URI"))
      )(ApplicativeError[F, Throwable].pure(_))
    }
  }

  object Response {
    def parser[F[_]: Sync](maxHeaderLength: Int)(s: Stream[F, Byte])(
        logger: Logger[F]): Resource[F, Response[F]] =
      s.through(httpHeaderAndBody[F](maxHeaderLength))
        .evalMap {
          case (bv, body) => headerBlobByteVectorToResponse[F](bv, body, maxHeaderLength)(logger)
        }
        .take(1)
        .compile
        .resource
        .lastOrError

    private def headerBlobByteVectorToResponse[F[_]](
        b: ByteVector,
        s: Stream[F, Byte],
        maxHeaderLength: Int)(logger: Logger[F])(implicit
        F: MonadError[F, Throwable]): F[Response[F]] =
      for {
        hE <- splitHeader(b)(logger)
        (methodHttpUri, headersBV) <- hE.fold(
          _ =>
            ApplicativeError[F, Throwable].raiseError[(ByteVector, ByteVector)](
              EmberException.ParseError("Invalid Empty Init Line")),
          Applicative[F].pure(_)
        )
        _ <- logger.trace(s"HeadersSection - ${headersBV.decodeAscii}")
        headers = HeaderP.headersInSection(headersBV) match {
          case Completed(headers, _) => headers
          case Incomplete(_, _,_, _, _) => ???
        }
        _ <- logger.trace(show"Headers: $headers")

        (httpV, status) <- bvToResponseTopLine[F](methodHttpUri)

        _ <- logger.trace(s"HttpVersion: $httpV - Status: $status")
      } yield {
        val baseResp = org.http4s.Response[F](
          status = status,
          httpVersion = httpV,
          headers = headers
        )
        val body =
          if (baseResp.isChunked) s.through(ChunkedEncoding.decode(maxHeaderLength))
          else s.take(baseResp.contentLength.getOrElse(0L))
        baseResp.withBodyStream(body)
      }

    private def bvToResponseTopLine[F[_]](
        b: ByteVector
    )(implicit F: MonadError[F, Throwable]): F[(HttpVersion, Status)] =
      // Status-Line = HTTP-Version SP Status-Code SP Reason-Phrase CRLF
      for {
        (httpV, restS) <- getHttpVersionEmitRest[F](b)
        status <- getStatus[F](restS)
      } yield (httpV, status)

    private def getHttpVersionEmitRest[F[_]](
        b: ByteVector
    )(implicit F: MonadError[F, Throwable]): F[(HttpVersion, String)] = {
      val opt = for {
        line <- b.decodeAscii.toOption
        idx <- Some(line.indexOf(' '))
        if idx >= 0
      } yield (line.substring(0, idx), line.substring(idx + 1))

      for {
        (httpS, rest) <- opt.fold(
          ApplicativeError[F, Throwable]
            .raiseError[(String, String)](EmberException.ParseError("Missing HttpVersion"))
        )(Applicative[F].pure(_))
        httpV <- HttpVersion.fromString(httpS).liftTo[F]
      } yield (httpV, rest)
    }

    private def getStatus[F[_]](s: String)(implicit F: MonadError[F, Throwable]): F[Status] = {
      def getFirstWord(text: String): String = {
        val index = text.indexOf(' ')
        if (index >= 0)
          text.substring(0, index)
        else text
      }
      for {
        word <- Either.catchNonFatal(getFirstWord(s)).liftTo[F]
        code <- Either.catchNonFatal(word.toInt).liftTo[F]
        status <- Status.fromInt(code).liftTo[F]
      } yield status
    }
  }
}
