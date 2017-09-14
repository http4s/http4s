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
    "decode GZIP encoding" in prop { body: Vector[Byte] =>
      (for {
        response <- Response[IO]().withBody(body.toArray)
        zipped = Compression.zipResponse[IO](bufferSize, Deflater.DEFAULT_COMPRESSION, response)
        unzipped = Compression.unzipResponse(bufferSize, zipped)
        decoded <- unzipped.as[Chunk[Byte]].map(_.toVector)
      } yield (decoded === body)).unsafeRunSync
    }
  }
}
