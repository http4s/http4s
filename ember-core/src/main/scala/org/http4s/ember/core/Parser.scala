package org.http4s.ember.core

import cats._
import cats.implicits._
import fs2._
import scodec.bits.ByteVector
import org.http4s._
import cats.effect._
import Shared._
import scala.annotation.tailrec

private[ember] object Parser {
    /**
    * From the stream of bytes this extracts Http Header and body part.
    */
  def httpHeaderAndBody[F[_]: ApplicativeError[?[_], Throwable]](maxHeaderSize: Int): Pipe[F, Byte, (ByteVector, Stream[F, Byte])] = {
    def go(buff: ByteVector, in: Stream[F, Byte]): Pull[F, (ByteVector, Stream[F, Byte]), Unit] = {
      in.pull.uncons flatMap {
        case None =>
          Pull.raiseError[F](EmberException.ParseError(s"Incomplete Header received (sz = ${buff.size}): ${buff.decodeUtf8}"))
        case Some((chunk, tl)) =>
          val bv = chunk2ByteVector(chunk)
          val all = buff ++ bv
          val idx = all.indexOfSlice(`\r\n\r\n`)
          if (idx < 0) {
            if (all.size > maxHeaderSize) Pull.raiseError[F](EmberException.ParseError(s"Size of the header exceeded the limit of $maxHeaderSize (${all.size})"))
            else go(all, tl)
          }
          else {
            val (h, t) = all.splitAt(idx)
            if (h.size > maxHeaderSize)  Pull.raiseError[F](EmberException.ParseError(s"Size of the header exceeded the limit of $maxHeaderSize (${all.size})"))
            else  Pull.output1((h, Stream.chunk(Chunk.ByteVectorChunk(t.drop(`\r\n\r\n`.size))) ++ tl))

          }
      }
    }
    src => go(ByteVector.empty, src).stream
  }

  @tailrec
  def generateHeaders(byteVector: ByteVector)(acc: Headers) : Headers = {
    val headerO = splitHeader(byteVector)
    // println(headerO)

    def generateHeaderForLine(bv: ByteVector): Option[Header] = {
      for {
          line <- bv.decodeAscii.toOption
          // _ = println(s"Generate Headers - line: ${line}")
          idx <- Some(line indexOf ':')
          if idx >= 0
          header = Header(line.substring(0, idx), line.substring(idx + 1).trim)
          // _ = println(s"generateHeaders - header: ${header}")
        } yield header
    }

    headerO match {
      case Right((lineBV, rest)) =>
        val headerO = generateHeaderForLine(lineBV)
        val newHeaders = acc ++ headerO.map(Headers.of(_)).foldMap(identity)
        // println(s"Generate Headers Header0 = $headerO")
        generateHeaders(rest)(newHeaders)
      case Left(bv) => 
        val headerO = generateHeaderForLine(bv)
        headerO.map(Headers.of(_)).foldMap(identity) ++ acc
    }

  }

  def splitHeader(byteVector: ByteVector): Either[ByteVector, (ByteVector, ByteVector)] = {
    val index = byteVector.indexOfSlice(`\r\n`)
    if (index >= 0L) {
      val (line, rest) = byteVector.splitAt(index)
      // println(s"splitHeader - Slice: ${line.decodeAscii}- Rest: ${rest.decodeAscii}")
      Either.right((line, rest.drop(`\r\n`.length)))
    }
    else {
      Either.left(byteVector)
    }
  }

  object Request {

    def parser[F[_]: Sync](maxHeaderLength: Int)(s: Stream[F, Byte]): F[Request[F]] =
      s.through(httpHeaderAndBody[F](maxHeaderLength))
        .evalMap{case (bv, body) => headerBlobByteVectorToRequest[F](bv, body, maxHeaderLength)}
        .take(1)
        .compile
        .lastOrError

    private def headerBlobByteVectorToRequest[F[_]: MonadError[?[_], Throwable]](b: ByteVector, s: Stream[F, Byte], maxHeaderLength: Int): F[Request[F]] = 
      for {

        (methodHttpUri, headersBV) <- splitHeader(b).fold(
          _ => ApplicativeError[F, Throwable].raiseError[(ByteVector, ByteVector)](EmberException.ParseError("Invalid Empty Init Line"))
          , Applicative[F].pure(_)
        )
        // Raw Header Logging
        // _ = println(s"HeadersSection - ${headersBV.decodeAscii}")

        headers = generateHeaders(headersBV)(Headers.empty)
        // _ = println(headers)

        (method, uri, http) <- bvToRequestTopLine[F](methodHttpUri)
        
        contentLength = headers.get(org.http4s.headers.`Content-Length`).map(_.length).getOrElse(0L)
        host = headers.get(org.http4s.headers.Host)
        isChunked = headers.get(org.http4s.headers.`Transfer-Encoding`).exists(_.value.toList.contains(TransferCoding.chunked))

        body = Alternative[Option].guard(isChunked).fold(
          s.take(contentLength)
        )(_ => s.through(ChunkedEncoding.decode(maxHeaderLength)))

        // enriched with host
        // seems presumptious
        newUri = uri.copy(authority = host.map(h => Uri.Authority(host = Uri.RegName(h.host), port = h.port)))
        newHeaders = headers.filterNot(_.is(org.http4s.headers.Host))

      } yield org.http4s.Request[F](
          method = method,
          uri = newUri,
          httpVersion = http,
          headers = newHeaders,
          body = body
        )

    private def bvToRequestTopLine[F[_]: MonadError[?[_], Throwable]](b: ByteVector): F[(Method, Uri, HttpVersion)] = for {
      (method, rest) <- getMethodEmitRest[F](b)
      (uri, httpVString) <- getUriEmitHttpVersion[F](rest)
      httpVersion <- HttpVersion.fromString(httpVString).liftTo[F]
      
    } yield (method, uri, httpVersion)


    private def getMethodEmitRest[F[_]: ApplicativeError[?[_], Throwable]](b: ByteVector): F[(Method, String)] = {
      val opt = for {
        line <- b.decodeAscii.toOption
        idx <- Some(line indexOf ' ')
        if (idx >= 0)
        out <- Method.fromString(line.substring(0, idx)).toOption
      } yield (out, line.substring(idx + 1))

      opt.fold(
        ApplicativeError[F, Throwable].raiseError[(Method, String)](EmberException.ParseError("Missing Method"))
        )(ApplicativeError[F, Throwable].pure(_))
    }

    private def getUriEmitHttpVersion[F[_]: ApplicativeError[?[_], Throwable]](s: String): F[(Uri, String)] = {
      val opt = for {
        idx <- Some(s indexOf ' ')
        if (idx >= 0)
        uri <- Uri.fromString(s.substring(0, idx)).toOption
      } yield (uri, s.substring(idx + 1))

      opt.fold(
        ApplicativeError[F, Throwable].raiseError[(Uri, String)](EmberException.ParseError("Missing URI"))
      )(ApplicativeError[F, Throwable].pure(_))
    }
  }

  object Response {

    def parser[F[_]: Sync](maxHeaderLength: Int)(s: Stream[F, Byte]): F[Response[F]] =
      s.through(httpHeaderAndBody[F](maxHeaderLength))
        .evalMap{case (bv, body) => headerBlobByteVectorToResponse[F](bv, body, maxHeaderLength)}
        .take(1)
        .compile
        .lastOrError

    private def headerBlobByteVectorToResponse[F[_]: MonadError[?[_], Throwable]](b: ByteVector, s: Stream[F, Byte], maxHeaderLength: Int): F[Response[F]] = 
      for {

        (methodHttpUri, headersBV) <- splitHeader(b).fold(
          _ => ApplicativeError[F, Throwable].raiseError[(ByteVector, ByteVector)](EmberException.ParseError("Invalid Empty Init Line"))
          , Applicative[F].pure(_)
        )
        headers = generateHeaders(headersBV)(Headers.empty)
        (httpV, status) <- bvToResponseTopLine[F](methodHttpUri)
        
        contentLength = headers.get(org.http4s.headers.`Content-Length`).map(_.length).getOrElse(0L)
        isChunked = headers.get(org.http4s.headers.`Transfer-Encoding`).exists(_.value.toList.contains(TransferCoding.chunked))

        body = Alternative[Option].guard(isChunked).fold(
          s.take(contentLength)
        )(_ => s.through(ChunkedEncoding.decode(maxHeaderLength)))

        
      } yield org.http4s.Response[F](
          status = status,
          httpVersion = httpV,
          headers = headers,
          body = body
        )

    private def bvToResponseTopLine[F[_]: MonadError[?[_], Throwable]](
      b: ByteVector
    ): F[(HttpVersion, Status)] = {
      // Status-Line = HTTP-Version SP Status-Code SP Reason-Phrase CRLF
      for {
        (httpV, restS) <- getHttpVersionEmitRest[F](b)
        status <- getStatus[F](restS)
      } yield (httpV, status)
    }

    private def getHttpVersionEmitRest[F[_]: MonadError[?[_], Throwable]](
      b: ByteVector
    ): F[(HttpVersion, String)] = {
      val opt = for {
        line <- b.decodeAscii.toOption
        idx <- Some(line indexOf ' ')
        if (idx >= 0)
      } yield (line.substring(0, idx), line.substring(idx + 1))

      for {
      (httpS, rest) <- opt.fold(
        ApplicativeError[F, Throwable].raiseError[(String, String)](
          EmberException.ParseError("Missing HttpVersion"))
        )(Applicative[F].pure(_))
      httpV <- HttpVersion.fromString(httpS).liftTo[F]
      } yield (httpV, rest)
    }
  
    private def getStatus[F[_]: MonadError[?[_], Throwable]](s: String): F[Status] = {
      def getFirstWord(text: String): String = {
        val index = text.indexOf(' ')
        if (index >= 0) {
          text.substring(0, index)
        } else text
      }
      for {
        word <- Either.catchNonFatal(getFirstWord(s)).liftTo[F]
        code <- Either.catchNonFatal(word.toInt).liftTo[F]
        status <- Status.fromInt(code).liftTo[F]
      } yield status
    }



    
  }

}