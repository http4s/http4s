package org.http4s.bench

import org.http4s._
import org.openjdk.jmh.annotations._

import cats.parse.Parser
import java.util.concurrent.TimeUnit

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
class HttpVersionDecodeBench {
  import HttpVersionDecodeBench._

  @Benchmark
  def parseNew: Either[Any, HttpVersion] =
    http1Parser.parseAll("HTTP/1.1")

  @Benchmark
  def parseOld: Either[Any, HttpVersion] =
    http1ParserOld.parseAll("HTTP/1.1")
}

object HttpVersionDecodeBench {
  val http1Parser = HttpVersion.http1Codec.foldMap(org.http4s.codec.catsParserDecoder)
  val http1ParserOld: Parser[HttpVersion] = {
    import cats.parse.{Parser => P}
    import cats.parse.Rfc5234.digit
    // HTTP-name = %x48.54.54.50 ; HTTP
    // HTTP-version = HTTP-name "/" DIGIT "." DIGIT
    val httpVersion = P.string("HTTP/") *> digit ~ (P.char('.') *> digit)

    httpVersion.map { case (major, minor) =>
      new HttpVersion(major - '0', minor - '0')
    }
  }
}
