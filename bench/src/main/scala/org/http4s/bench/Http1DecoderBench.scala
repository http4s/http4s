/*
 * Copyright 2015 http4s.org
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
package bench

import org.openjdk.jmh.annotations._

@Threads(1)
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
class Http1DecoderBench {
  @Benchmark
  def http1Decoder: Either[ParseFailure, (Int, ResponsePrelude)] =
    Http1DecoderBench.http1Decoder.decode(Http1DecoderBench.chunk)

  @Benchmark
  def ember: Either[Throwable, (Int, ResponsePrelude)] = {
    import org.http4s.ember.core.Parser.Response.RespPrelude._
    import org.http4s.ember.core.Parser._

    val arr = Http1DecoderBench.chunk.toArray
    parsePrelude[Either[Throwable, *]](arr, 1024).flatMap {
      case Left(_) => Left(new Exception("WTF?"))
      case Right(RespPrelude(version, status, next)) =>
        HeaderP.parseHeaders[Either[Throwable, *]](arr, next, 1024).flatMap {
          case Left(_) => Left(new Exception("WTF?"))
          case Right(HeaderP(headers, _, _, i)) =>
            Right(
              (
                i,
                ResponsePrelude(
                  httpVersion = version,
                  status = status,
                  headers = headers
                )))
        }
    }
  }
}

object Http1DecoderBench {
  val resp = ResponsePrelude(
    httpVersion = HttpVersion.`HTTP/1.1`,
    status = Status.Ok,
    headers = Headers(
      "Access-Control-Allow-Credentials" -> "true",
      "Access-Control-Allow-Origin" -> "*",
      "Connection" -> "keep-alive",
      "Content-Length" -> "9593",
      "Content-Type" -> "text/html; charset=utf-8",
      "Date" -> "Sun, 10 Oct 2021 03:34:47 GMT",
      "Server" -> "gunicorn/19.9.0"
    )
  )
  val chunk = ResponsePrelude.http1Codec.encode(resp)

  val decoder = Http1Decoder

  import cats.parse.{Parser => P}
  import cats.parse.Rfc5234
  import org.http4s.internal.parsing.Rfc7230
  import org.typelevel.ci._

  // This parser is sloppy.
  private val parser: P[ResponsePrelude] = {
    val statusCode: P[Status] = Rfc5234.digit.rep(3).string.mapFilter { code =>
      Status.fromInt(code.toInt).fold(_ => None, Some(_))
    }
    val reasonPhrase = Rfc5234.vchar.orElse(P.charIn(" \t")).rep
    val status: P[Status] = statusCode <* P.char(' ') <* reasonPhrase

    val fieldValue = Rfc5234.vchar.rep.repSep(P.charIn(" \t")).string
    val header = ((Rfc7230.token.string <* P.char(
      ':') <* Rfc7230.ows) ~ (fieldValue <* Rfc7230.ows <* P.string("\r\n"))).map { case (k, v) =>
      Header.Raw(CIString(k), v)
    }
    val headers = header.rep0 <* P.string("\r\n")

    (HttpVersion.parser ~ (P.char(' ') *> status <* P.string("\r\n")) ~ headers).map {
      case ((httpVersion, status), headers) =>
        ResponsePrelude(httpVersion = httpVersion, status = status, headers = Headers(headers))
    }
  }

  val http1Decoder: Http1Decoder[ResponsePrelude] =
    Http1Decoder.catsParse(parser, "Invalid response prelude")
}
