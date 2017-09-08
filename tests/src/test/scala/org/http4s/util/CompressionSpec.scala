package org.http4s.util

import java.util.zip.Deflater

import cats.data.EitherT
import cats.effect.IO
import fs2.Chunk
import org.http4s._
import org.scalacheck.Prop.forAll
import org.scalacheck.Properties

import scala.concurrent.ExecutionContext

class CompressionSpec extends Http4sSpec {
  implicit val executionContext: ExecutionContext = TrampolineExecutionContext

  val bufferSize = 32 * 1024

  "Compression" should {
    checkAll(
      "decode GZIP encoding",
      new Properties("GZip") {
        property("encode(decode)") = forAll {
          body: Array[Byte] =>
            val t: IO[Either[DecodeFailure, Chunk[Byte]]] =
              EntityEncoder[IO, Array[Byte]]
                .toEntity(body)
                .flatMap {
                  entity =>
                    val response = Response[IO](body = entity.body)
                    val zipped =
                      Compression
                        .zipResponse[IO](bufferSize, Deflater.DEFAULT_COMPRESSION, response)
                    val unzipped = Compression.unzipResponse(bufferSize, zipped)
                    val decoded: IO[Either[DecodeFailure, Chunk[Byte]]] =
                      EntityDecoder.binary[IO].decode(unzipped, strict = false).value
                    decoded
                }
            EitherT(t) must returnRight(Chunk.bytes(body))
        }
      }
    )
  }
}
